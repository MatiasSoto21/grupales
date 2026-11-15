package com.example.facturacion.services;

import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class AfipWsaaServiceTest {
    @TempDir Path temporary;
    @Test
    void ticketSurvivesServiceRestartAndIsNotRequestedAgain() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        var name = new X500Name("CN=Local WSAA test");
        var builder = new JcaX509v3CertificateBuilder(name, java.math.BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(60)), Date.from(Instant.now().plusSeconds(3600)), name, pair.getPublic());
        var certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        Path key = temporary.resolve("private.key");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        Path cert = temporary.resolve("certificate.crt");
        Files.write(cert, certificate.getEncoded());
        AtomicInteger calls = new AtomicInteger();
        var first = service(calls);
        var second = service(calls);
        var auth = first.obtenerTokenYSign(key.toString(), cert.toString());
        assertArrayEquals(new String[]{"test-token", "test-sign"}, auth);
        auth[0] = "altered";
        assertArrayEquals(new String[]{"test-token", "test-sign"}, second.obtenerTokenYSign(key.toString(), cert.toString()));
        assertEquals(1, calls.get());
    }
    private AfipWsaaService service(AtomicInteger calls) {
        var service = new AfipWsaaService() {
            @Override protected String enviarSoap(String url, String xml) {
                calls.incrementAndGet();
                return "<r><loginCmsReturn>&lt;ticket&gt;&lt;token&gt;test-token&lt;/token&gt;"
                        + "&lt;sign&gt;test-sign&lt;/sign&gt;&lt;expirationTime&gt;" + Instant.now().plusSeconds(600)
                        + "&lt;/expirationTime&gt;&lt;/ticket&gt;</loginCmsReturn></r>";
            }
        };
        ReflectionTestUtils.setField(service, "cacheDirectory", temporary.resolve("cache").toString());
        return service;
    }
    @Test
    void expiredTicketsAreIgnored() throws Exception {
        var store = new WsaaTicketStore(temporary, "test-cert");
        store.write(new WsaaTicketStore.Ticket("token", "sign", Instant.now().minusSeconds(1)));
        assertNull(store.read());
    }
}
