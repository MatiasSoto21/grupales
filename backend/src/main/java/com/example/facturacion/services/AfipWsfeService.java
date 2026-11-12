package com.example.facturacion.services;

import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AfipWsfeService {

    private static final String URL_WSFE_HOMO = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx";

    /**
     * Obtiene el último número de comprobante emitido para un punto de venta y tipo de comprobante.
     */
    public int obtenerUltimoComprobante(String token, String sign, long cuit, int ptoVta, int cbteTipo) throws Exception {
        String soapEnvelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<soap:Body>" +
                "<FECompUltimoAutorizado xmlns=\"http://ar.gov.afip.dif.FEV1/\">" +
                "<Auth>" +
                "<Token>" + token + "</Token>" +
                "<Sign>" + sign + "</Sign>" +
                "<Cuit>" + cuit + "</Cuit>" +
                "</Auth>" +
                "<PtoVta>" + ptoVta + "</PtoVta>" +
                "<CbteTipo>" + cbteTipo + "</CbteTipo>" +
                "</FECompUltimoAutorizado>" +
                "</soap:Body>" +
                "</soap:Envelope>";

        String xmlResponse = enviarSoap(URL_WSFE_HOMO, soapEnvelope, "http://ar.gov.afip.dif.FEV1/FECompUltimoAutorizado");
        String cbteNroStr = extraerValorEtiqueta(xmlResponse, "CbteNro");

        return cbteNroStr != null ? Integer.parseInt(cbteNroStr) : 0;
    }

    /**
     * Solicita CAE para una Factura B a Consumidor Final / Cliente.
     */
   public String[] solicitarCaeFacturaB(String token, String sign, long cuitEmisor, int ptoVta, int cbteNro, 
                                        int docTipo, long docNro, double importeTotal, int condicionIvaReceptor) throws Exception {
        
        String fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        double neto = Math.round((importeTotal / 1.21) * 100.0) / 100.0;
        double iva = Math.round((importeTotal - neto) * 100.0) / 100.0;

        String soapEnvelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<soap:Body>" +
                "<FECAESolicitar xmlns=\"http://ar.gov.afip.dif.FEV1/\">" +
                "<Auth>" +
                "<Token>" + token + "</Token>" +
                "<Sign>" + sign + "</Sign>" +
                "<Cuit>" + cuitEmisor + "</Cuit>" +
                "</Auth>" +
                "<FeCAEReq>" +
                "<FeCabReq>" +
                "<CantReg>1</CantReg>" +
                "<PtoVta>" + ptoVta + "</PtoVta>" +
                "<CbteTipo>6</CbteTipo>" + // 6 = Factura B
                "</FeCabReq>" +
                "<FeDetReq>" +
                "<FECAEDetRequest>" +
                "<Concepto>1</Concepto>" + // 1 = Productos
                "<DocTipo>" + docTipo + "</DocTipo>" + // 96 = DNI, 80 = CUIT, 99 = Consumidor Final sin doc
                "<DocNro>" + docNro + "</DocNro>" +
                "<CbteDesde>" + cbteNro + "</CbteDesde>" +
                "<CbteHasta>" + cbteNro + "</CbteHasta>" +
                "<CbteFch>" + fechaHoy + "</CbteFch>" +
                "<ImpTotal>" + String.format("%.2f", importeTotal).replace(",", ".") + "</ImpTotal>" +
                "<ImpTotConc>0.00</ImpTotConc>" +
                "<ImpNeto>" + String.format("%.2f", neto).replace(",", ".") + "</ImpNeto>" +
                "<ImpOpEx>0.00</ImpOpEx>" +
                "<ImpTrib>0.00</ImpTrib>" +
                "<ImpIVA>" + String.format("%.2f", iva).replace(",", ".") + "</ImpIVA>" +
                "<FchServDesde></FchServDesde>" +
                "<FchServHasta></FchServHasta>" +
                "<FchVtoPago></FchVtoPago>" +
                "<MonId>PES</MonId>" +
                "<MonCotiz>1</MonCotiz>" + // Nombre corregido según esquema WSFE v1
                "<CondicionIVAReceptorId>" + condicionIvaReceptor + "</CondicionIVAReceptorId>" +
                "<Iva>" +
                "<AlicIva>" +
                "<Id>5</Id>" + // 5 = 21%
                "<BaseImp>" + String.format("%.2f", neto).replace(",", ".") + "</BaseImp>" +
                "<Importe>" + String.format("%.2f", iva).replace(",", ".") + "</Importe>" +
                "</AlicIva>" +
                "</Iva>" +
                "</FECAEDetRequest>" +
                "</FeDetReq>" +
                "</FeCAEReq>" +
                "</FECAESolicitar>" +
                "</soap:Body>" +
                "</soap:Envelope>";

        String xmlResponse = enviarSoap(URL_WSFE_HOMO, soapEnvelope, "http://ar.gov.afip.dif.FEV1/FECAESolicitar");
        
        String cae = extraerValorEtiqueta(xmlResponse, "CAE");
        String caeFchVto = extraerValorEtiqueta(xmlResponse, "CAEFchVto");
        String resultado = extraerValorEtiqueta(xmlResponse, "Resultado");

        if (!"A".equals(resultado) || cae == null) {
            String msgObs = extraerValorEtiqueta(xmlResponse, "Msg");
            throw new RuntimeException("AFIP Rechazó la solicitud. Resultado: " + resultado + " | Obs/Error: " + msgObs + " | XML completo: " + xmlResponse);
        }

        return new String[] {cae, caeFchVto, String.valueOf(cbteNro)};
    }

    private String extraerValorEtiqueta(String xml, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">");
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String enviarSoap(String urlStr, String soapXml, String soapAction) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        conn.setRequestProperty("SOAPAction", soapAction);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(soapXml.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 200 && responseCode < 300) 
                         ? conn.getInputStream() 
                         : conn.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}