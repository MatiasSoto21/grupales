package com.example.facturacion.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AfipWsfeServiceTest {
    private String response;
    private String request;
    private final AfipWsfeService service = new AfipWsfeService() {
        @Override
        protected String enviarSoap(String url, String xml, String action) {
            request = xml;
            return response;
        }
    };

    @Test
    void lastNumberHandlesNamespacesAndErrorsWithoutGuessingZero() throws Exception {
        response = "<a:r xmlns:a='urn:test'><a:CbteNro>123</a:CbteNro></a:r>";
        assertEquals(123, service.obtenerUltimoComprobante("token", "sign", 1, 1, 6));
        response = "<r><Errors><Msg>Punto de venta invalido</Msg></Errors></r>";
        assertThrows(IllegalStateException.class, () -> service.obtenerUltimoComprobante("token", "sign", 1, 1, 6));
        response = "<r><CbteNro>0</CbteNro></r>";
        assertEquals(0, service.obtenerUltimoComprobante("token", "sign", 1, 1, 6));
    }

    @Test
    void exactDetailTaxAmountsAreSentInsteadOfReconstructedTotals() throws Exception {
        response = authorized("70000000001234");
        service.solicitarCaeFacturaB("token", "sign", 1, 1, 10, 99, 0, 0.06, 0.06, 0.0, 5);
        assertTrue(request.contains("<ImpNeto>0.06</ImpNeto>"));
        assertTrue(request.contains("<ImpIVA>0.00</ImpIVA>"));
    }

    @Test
    void blankCaeAndRejectedDetailCannotBeAccepted() {
        response = authorized("");
        assertThrows(IllegalStateException.class, () -> service.solicitarCaeFacturaB("token", "sign", 1, 1, 10, 99, 0, 121, 5));
        response = "<r><Resultado>A</Resultado><FECAEDetResponse><Resultado>R</Resultado>"
                + "<CAE>70000000001234</CAE><CAEFchVto>20300101</CAEFchVto></FECAEDetResponse></r>";
        assertThrows(IllegalStateException.class, () -> service.solicitarCaeFacturaB("token", "sign", 1, 1, 10, 99, 0, 121, 5));
    }

    @Test
    void escapedWsaaTicketIsDecodedByXmlParser() throws Exception {
        assertEquals("<ticket><token>a&amp;b</token></ticket>", AfipSoapSupport.valor(
                "<r><loginCmsReturn>&lt;ticket&gt;&lt;token&gt;a&amp;amp;b&lt;/token&gt;&lt;/ticket&gt;</loginCmsReturn></r>", "loginCmsReturn"));
    }

    @Test void classAAndExplicitBusinessErrorsAreRecognized() throws Exception {
        response = authorized("70000000001234");
        service.solicitarCaeFactura("token", "sign", 1, 1, 10, 1, 80, 20123456789L, 121, 100, 21, 1);
        assertTrue(request.contains("<CbteTipo>1</CbteTipo>"));
        response = "<r><Errors><Err><Code>10017</Code><Msg>CUIT no registrado</Msg></Err></Errors></r>";
        var error = assertThrows(ArcaRechazoException.class, () -> service.solicitarCaeFacturaB("token", "sign", 1, 1, 10, 99, 0, 121, 5));
        assertTrue(error.getMessage().contains("10017"));
        response = authorized("");
        var incomplete = assertThrows(IllegalStateException.class, () -> service.solicitarCaeFacturaB("token", "sign", 1, 1, 10, 99, 0, 121, 5));
        assertFalse(incomplete instanceof ArcaRechazoException);
    }

    @Test void selectedFiscalDateIsSentExactlyWithoutReplacingItWithToday() throws Exception {
        response = authorized("70000000001234");
        service.solicitarCaeFactura("token", "sign", 1, 1, 10, 6, 99, 0, 121, 100, 21, 5, java.time.LocalDate.of(2026, 11, 12));
        assertTrue(request.contains("<CbteFch>20261112</CbteFch>"));
    }

    @Test void lastAuthorizedDateMustComeFromTheMatchingReceiptResponse() throws Exception {
        response = "<r><ResultGet><Resultado>A</Resultado><PtoVta>1</PtoVta><CbteTipo>6</CbteTipo><CbteDesde>10</CbteDesde><CbteFch>20261112</CbteFch></ResultGet></r>";
        assertEquals(java.time.LocalDate.of(2026, 11, 12), service.obtenerFechaComprobanteAutorizado("token", "sign", 1, 1, 6, 10));
        assertTrue(request.contains("<FECompConsultar")); assertTrue(request.contains("<CbteNro>10</CbteNro>"));
        assertThrows(IllegalStateException.class, () -> service.obtenerFechaComprobanteAutorizado("token", "sign", 1, 1, 6, 11));
        response = "<r><Errors><Err><Msg>No encontrado</Msg></Err></Errors></r>";
        assertThrows(IllegalStateException.class, () -> service.obtenerFechaComprobanteAutorizado("token", "sign", 1, 1, 6, 10));
    }

    private String authorized(String cae) {
        return "<a:r xmlns:a='urn:test'><a:FECAEDetResponse><a:Resultado>A</a:Resultado>"
                + "<a:CAE>" + cae + "</a:CAE><a:CAEFchVto>20300101</a:CAEFchVto></a:FECAEDetResponse></a:r>";
    }
}
