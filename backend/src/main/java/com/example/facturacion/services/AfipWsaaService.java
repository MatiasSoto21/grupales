package com.example.facturacion.services;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;

@Service
public class AfipWsaaService {

    private static final String URL_WSAA_HOMO = "https://wsaahomo.afip.gov.ar/ws/services/LoginCms";

    @org.springframework.beans.factory.annotation.Value("${facturacion.arca.wsaa-cache-dir:}")
    private String cacheDirectory = "";

    private String[] cachedAuth;
    private String cachedKey;
    private String cachedCert;
    private java.time.Instant cachedUntil = java.time.Instant.EPOCH;

    public synchronized String[] obtenerTokenYSign(String keyPath, String certPath) throws Exception {
        if (cachedAuth != null && keyPath.equals(cachedKey) && certPath.equals(cachedCert)
                && java.time.Instant.now().isBefore(cachedUntil)) return cachedAuth.clone();
        X509Certificate cert = cargarCertificado(certPath);
        String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        java.nio.file.Path directory = cacheDirectory == null || cacheDirectory.isBlank()
                ? java.nio.file.Path.of(System.getProperty("user.home"), ".facturacion", "wsaa")
                : java.nio.file.Path.of(cacheDirectory);
        WsaaTicketStore store = new WsaaTicketStore(directory, fingerprint);
        try (var channel = store.openLock(); var lock = channel.lock()) {
            WsaaTicketStore.Ticket stored = store.read();
            if (stored != null) {
                cachedAuth = new String[]{stored.token(), stored.sign()};
                cachedUntil = stored.expiration().minusSeconds(60);
                cachedKey = keyPath;
                cachedCert = certPath;
                return cachedAuth.clone();
            }
            return requestNewTicket(keyPath, certPath, cert, store);
        }
    }

    private String[] requestNewTicket(String keyPath, String certPath, X509Certificate cert, WsaaTicketStore store) throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        PrivateKey privateKey = cargarClavePrivada(keyPath);

        ZonedDateTime now = ZonedDateTime.now();
        String genTime = now.minusMinutes(5).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String expTime = now.plusMinutes(10).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String traXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<loginTicketRequest version=\"1.0\">" +
                "<header>" +
                "<uniqueId>" + (System.currentTimeMillis() / 1000) + "</uniqueId>" +
                "<generationTime>" + genTime + "</generationTime>" +
                "<expirationTime>" + expTime + "</expirationTime>" +
                "</header>" +
                "<service>wsfe</service>" +
                "</loginTicketRequest>";

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner sha256Signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(privateKey);
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(sha256Signer, cert));
        gen.addCertificates(new JcaCertStore(Collections.singletonList(cert)));

        CMSSignedData signedData = gen.generate(
                new org.bouncycastle.cms.CMSProcessableByteArray(traXml.getBytes(StandardCharsets.UTF_8)), true);
        String cmsBase64 = Base64.getEncoder().encodeToString(signedData.getEncoded());

        String soapEnvelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "xmlns:nso=\"http://wsaa.afip.gov.ar/ws/services/LoginCms\">" +
                "<soapenv:Header/><soapenv:Body><nso:loginCms><nso:in0>" + cmsBase64 +
                "</nso:in0></nso:loginCms></soapenv:Body></soapenv:Envelope>";

        String xmlResponse = enviarSoap(URL_WSAA_HOMO, soapEnvelope);

        String xmlLimpio = AfipSoapSupport.valor(xmlResponse, "loginCmsReturn");
        if (xmlLimpio == null) throw new IOException("WSAA no devolvio un ticket de acceso");

        String token = extraerValorEtiqueta(xmlLimpio, "token");
        String sign = extraerValorEtiqueta(xmlLimpio, "sign");

        if (token == null || sign == null) {
            throw new RuntimeException("No se pudieron extraer Token/Sign de la respuesta de ARCA");
        }

        String expiration = AfipSoapSupport.valor(xmlLimpio, "expirationTime");
        java.time.Instant expiresAt = java.time.OffsetDateTime.parse(expiration).toInstant();
        store.write(new WsaaTicketStore.Ticket(token, sign, expiresAt));
        cachedUntil = expiresAt.minusSeconds(60);
        cachedAuth = new String[]{token, sign};
        cachedKey = keyPath;
        cachedCert = certPath;
        return cachedAuth.clone();
    }

    private String extraerValorEtiqueta(String xml, String tag) throws Exception {
        return AfipSoapSupport.valor(xml, tag);
    }

    private PrivateKey cargarClavePrivada(String path) throws Exception {
        String content;
        try (InputStream stream = abrirCredencial(path)) {
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        content = content.replace("-----BEGIN PRIVATE KEY-----", "")
                         .replace("-----END PRIVATE KEY-----", "")
                         .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(content);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private X509Certificate cargarCertificado(String path) throws Exception {
        try (InputStream fis = abrirCredencial(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(fis);
        }
    }

    private InputStream abrirCredencial(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            return new org.springframework.core.io.ClassPathResource(path.substring("classpath:".length())).getInputStream();
        }
        return Files.newInputStream(java.nio.file.Path.of(path));
    }

    protected String enviarSoap(String urlStr, String soapXml) throws Exception {
        java.net.URL url = java.net.URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        conn.setRequestProperty("SOAPAction", ""); // Header obligatorio para WSAA/AFIP/ARCA
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(soapXml.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 200 && responseCode < 300)
                         ? conn.getInputStream()
                         : conn.getErrorStream();

        if (is == null) throw new IOException("WSAA devolvio HTTP " + responseCode + " sin respuesta");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);

            if (responseCode < 200 || responseCode >= 300) {
                String detail = AfipSoapSupport.valor(sb.toString(), "faultstring");
                throw new IOException("WSAA devolvio HTTP " + responseCode + (detail == null ? "" : ": " + detail));
            }
            return sb.toString();
        }
    }
}
