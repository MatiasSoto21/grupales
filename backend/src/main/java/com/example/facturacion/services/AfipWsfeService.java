package com.example.facturacion.services;

import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

        if (cbteNroStr == null) {
            throw new IllegalStateException("ARCA no devolvio el ultimo comprobante: "
                    + extraerValorEtiqueta(xmlResponse, "Msg"));
        }
        int numero = Integer.parseInt(cbteNroStr);
        if (numero < 0 || numero == Integer.MAX_VALUE) {
            throw new IllegalStateException("Numeracion ARCA fuera de rango");
        }
        return numero;
    }

    /**
     * Solicita CAE para una Factura B a Consumidor Final / Cliente.
     */
   public String[] solicitarCaeFacturaB(String token, String sign, long cuitEmisor, int ptoVta, int cbteNro,
                                        int docTipo, long docNro, double importeTotal, int condicionIvaReceptor) throws Exception {

        double neto = java.math.BigDecimal.valueOf(importeTotal)
                .divide(new java.math.BigDecimal("1.21"), 2, java.math.RoundingMode.HALF_UP).doubleValue();
        double iva = java.math.BigDecimal.valueOf(importeTotal).subtract(java.math.BigDecimal.valueOf(neto)).doubleValue();
        return solicitarCaeFacturaB(token, sign, cuitEmisor, ptoVta, cbteNro, docTipo, docNro,
                importeTotal, neto, iva, condicionIvaReceptor);
    }

    public String[] solicitarCaeFacturaB(String token, String sign, long cuitEmisor, int ptoVta, int cbteNro,
            int docTipo, long docNro, double importeTotal, double neto, double iva, int condicionIvaReceptor) throws Exception {
        return solicitarCaeFactura(token, sign, cuitEmisor, ptoVta, cbteNro, 6,
                docTipo, docNro, importeTotal, neto, iva, condicionIvaReceptor);
    }

    public String[] solicitarCaeFactura(String token, String sign, long cuitEmisor, int ptoVta, int cbteNro,
            int cbteTipo, int docTipo, long docNro, double importeTotal, double neto, double iva,
            int condicionIvaReceptor) throws Exception {
        return solicitarCaeFactura(token, sign, cuitEmisor, ptoVta, cbteNro, cbteTipo, docTipo, docNro,
                importeTotal, neto, iva, condicionIvaReceptor, LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")));
    }

    public String[] solicitarCaeFactura(String token, String sign, long cuitEmisor, int ptoVta, int cbteNro,
            int cbteTipo, int docTipo, long docNro, double importeTotal, double neto, double iva,
            int condicionIvaReceptor, LocalDate fechaEmision) throws Exception {
        if (fechaEmision == null) throw new IllegalArgumentException("La fecha fiscal es obligatoria");
        if (cbteTipo != 1 && cbteTipo != 6) throw new IllegalArgumentException("Tipo de comprobante no admitido");
        if (!Double.isFinite(importeTotal) || !Double.isFinite(neto) || !Double.isFinite(iva)
                || importeTotal < 0 || neto < 0 || iva < 0 || Math.abs(importeTotal - neto - iva) > 0.005) {
            throw new IllegalArgumentException("Importes de factura no validos");
        }
        String fechaComprobante = fechaEmision.format(DateTimeFormatter.BASIC_ISO_DATE);

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
                "<CbteTipo>" + cbteTipo + "</CbteTipo>" +
                "</FeCabReq>" +
                "<FeDetReq>" +
                "<FECAEDetRequest>" +
                "<Concepto>1</Concepto>" + // 1 = Productos
                "<DocTipo>" + docTipo + "</DocTipo>" + // 96 = DNI, 80 = CUIT, 99 = Consumidor Final sin doc
                "<DocNro>" + docNro + "</DocNro>" +
                "<CbteDesde>" + cbteNro + "</CbteDesde>" +
                "<CbteHasta>" + cbteNro + "</CbteHasta>" +
                "<CbteFch>" + fechaComprobante + "</CbteFch>" +
                "<ImpTotal>" + String.format(java.util.Locale.ROOT, "%.2f", importeTotal).replace(",", ".") + "</ImpTotal>" +
                "<ImpTotConc>0.00</ImpTotConc>" +
                "<ImpNeto>" + String.format(java.util.Locale.ROOT, "%.2f", neto).replace(",", ".") + "</ImpNeto>" +
                "<ImpOpEx>0.00</ImpOpEx>" +
                "<ImpTrib>0.00</ImpTrib>" +
                "<ImpIVA>" + String.format(java.util.Locale.ROOT, "%.2f", iva).replace(",", ".") + "</ImpIVA>" +
                "<FchServDesde></FchServDesde>" +
                "<FchServHasta></FchServHasta>" +
                "<FchVtoPago></FchVtoPago>" +
                "<MonId>PES</MonId>" +
                "<MonCotiz>1</MonCotiz>" + // Nombre corregido según esquema WSFE v1
                "<CondicionIVAReceptorId>" + condicionIvaReceptor + "</CondicionIVAReceptorId>" +
                "<Iva>" +
                "<AlicIva>" +
                "<Id>5</Id>" + // 5 = 21%
                "<BaseImp>" + String.format(java.util.Locale.ROOT, "%.2f", neto).replace(",", ".") + "</BaseImp>" +
                "<Importe>" + String.format(java.util.Locale.ROOT, "%.2f", iva).replace(",", ".") + "</Importe>" +
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
        String resultado = AfipSoapSupport.valor(xmlResponse, "FECAEDetResponse", "Resultado");

        if ("R".equals(resultado) || (resultado == null && extraerValorEtiqueta(xmlResponse, "Err") != null)) {
            throw new ArcaRechazoException("ARCA rechazo la solicitud. Codigo: "
                    + extraerValorEtiqueta(xmlResponse, "Code") + " | Detalle: " + extraerValorEtiqueta(xmlResponse, "Msg"));
        }
        if (!"A".equals(resultado) || cae == null || !cae.matches("[0-9]{14}") || caeFchVto == null) {
            String msgObs = extraerValorEtiqueta(xmlResponse, "Msg");
            throw new IllegalStateException("ARCA no confirmo una autorizacion completa. Resultado: " + resultado + " | Detalle: " + msgObs);
        }
        LocalDate.parse(caeFchVto, DateTimeFormatter.BASIC_ISO_DATE);

        return new String[] {cae, caeFchVto, String.valueOf(cbteNro)};
    }

    public LocalDate obtenerFechaComprobanteAutorizado(String token, String sign, long cuit, int ptoVta, int cbteTipo, int numero) throws Exception {
        if (numero <= 0) throw new IllegalArgumentException("Debe consultar un numero autorizado positivo");
        String soap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<FECompConsultar xmlns=\"http://ar.gov.afip.dif.FEV1/\"><Auth><Token>" + token
                + "</Token><Sign>" + sign + "</Sign><Cuit>" + cuit + "</Cuit></Auth>"
                + "<FeCompConsReq><CbteTipo>" + cbteTipo + "</CbteTipo><CbteNro>" + numero
                + "</CbteNro><PtoVta>" + ptoVta + "</PtoVta></FeCompConsReq></FECompConsultar></soap:Body></soap:Envelope>";
        String xml = enviarSoap(URL_WSFE_HOMO, soap, "http://ar.gov.afip.dif.FEV1/FECompConsultar");
        String resultado = AfipSoapSupport.valor(xml, "ResultGet", "Resultado");
        String fecha = AfipSoapSupport.valor(xml, "ResultGet", "CbteFch");
        if (!"A".equals(resultado) || fecha == null
                || !String.valueOf(ptoVta).equals(AfipSoapSupport.valor(xml, "ResultGet", "PtoVta"))
                || !String.valueOf(cbteTipo).equals(AfipSoapSupport.valor(xml, "ResultGet", "CbteTipo"))
                || !String.valueOf(numero).equals(AfipSoapSupport.valor(xml, "ResultGet", "CbteDesde")))
            throw new IllegalStateException("ARCA no devolvio la fecha verificable del ultimo comprobante autorizado");
        return LocalDate.parse(fecha, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String extraerValorEtiqueta(String xml, String tag) throws Exception {
        return AfipSoapSupport.valor(xml, tag);
    }

    protected String enviarSoap(String urlStr, String soapXml, String soapAction) throws Exception {
        java.net.URL url = java.net.URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
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

        if (is == null) throw new IOException("ARCA devolvio HTTP " + responseCode + " sin respuesta");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("ARCA devolvio HTTP " + responseCode);
            }
            return sb.toString();
        }
    }
}
