package com.example.facturacion.services;

import com.example.facturacion.FacturacionApplication;
import com.example.facturacion.dto.*;
import com.example.facturacion.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = FacturacionApplication.class, properties = {
    "facturacion.datos-iniciales.emitir-arca=false",
    "debug=false", "facturacion.arca-test.enabled=false", "logging.level.root=WARN",
    "facturacion.arca.ambiente=HOMOLOGACION",
    "facturacion.comprobante.emisor.denominacion=Emisor de prueba", "facturacion.comprobante.emisor.condicion-iva=Responsable Inscripto",
    "facturacion.comprobante.emisor.domicilio=Domicilio de prueba 123"
})
@AutoConfigureMockMvc
class ReceptorIntegrationTest {
    @MockBean AfipWsaaService wsaa;
    @MockBean AfipWsfeService wsfe;
    @Autowired FacturaService service;
    @Autowired ClienteRepository clients;
    @Autowired CondicionIvaRepository vat;
    @Autowired PuntoVentaRepository points;
    @Autowired TipoMonedaRepository currencies;
    @Autowired ListaPrecioArticuloRepository prices;
    @Autowired MockMvc mvc;
    @Autowired FacturaVentaRepository invoices;
    @Autowired FacturaVentaConsultasRepository consultas;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager persistenceContext;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagers;

    @BeforeEach void simulateOnlyExternalServices() throws Exception {
        when(wsaa.obtenerTokenYSign(anyString(), anyString())).thenReturn(new String[]{"token", "sign"});
        when(wsfe.obtenerUltimoComprobante(anyString(), anyString(), anyLong(), anyInt(), anyInt())).thenReturn(10000);
        when(wsfe.obtenerFechaComprobanteAutorizado(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(java.time.LocalDate.now().minusDays(5));
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenReturn(new String[]{"70000000001234", "20300101"});
    }
    private FacturaRequestDTO request() {
        var client = clients.findAll().get(0);
        var registered = vat.findAll().stream().filter(v -> v.getCodigoAfip() == 1).findFirst().orElseThrow();
        client.setCuitCuil("00-12345678-9"); client.setCondicionIva(registered);
        var point = points.findAll().stream().filter(p -> p.getNumero() == 1).findFirst().orElseThrow();
        var currency = currencies.findAll().stream().filter(c -> "PES".equals(c.getCodigoAfip())).findFirst().orElseThrow();
        var price = prices.findAll().get(0);
        return new FacturaRequestDTO(null, null, client.getId(), registered.getId(), currency.getId(), point.getId(), "EMITIDA", "Demo",
                List.of(new FacturaDetalleRequestDTO(price.getArticulo().getId(), price.getId(), "Articulo de prueba", 1, 0)));
    }
    @Test @Transactional void authorizedSnapshotAndQrPreserveDeclaredCuitAndClassA() throws Exception {
        var invoice = service.crearFactura(request());
        assertEquals("AUTORIZADA", invoice.getEstado()); assertFalse(invoice.isReceptorPrueba());
        assertEquals(80, invoice.getReceptorTipoDocumentoAfip()); assertEquals(123456789L, invoice.getReceptorNumeroDocumentoAfip());
        assertEquals("00123456789", invoice.getReceptorCuitCuilAfip()); assertEquals(1, invoice.getTipoComprobanteAfip());
        verify(wsfe).solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), eq(10001), eq(1), eq(80), eq(123456789L), anyDouble(), anyDouble(), anyDouble(), eq(1), any(java.time.LocalDate.class));
        var receipt = service.obtenerComprobante(invoice.getId());
        String url = new ComprobanteFacturaService().generarUrlQr(receipt);
        String payload = java.net.URLDecoder.decode(url.substring(url.indexOf("?p=") + 3), java.nio.charset.StandardCharsets.UTF_8);
        var qr = new ObjectMapper().readTree(java.util.Base64.getDecoder().decode(payload));
        assertEquals(80, qr.get("tipoDocRec").asInt()); assertEquals(123456789L, qr.get("nroDocRec").asLong());
        assertEquals(1, qr.get("tipoCmp").asInt());
    }
    @Test @Transactional void explicitRejectionIsPersistedWithoutCaeOrQr() throws Exception {
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenThrow(new ArcaRechazoException("10017: CUIT receptor no registrado en padron"));
        var invoice = service.crearFactura(request());
        assertEquals("RECHAZADA", invoice.getEstado()); assertEquals("R", invoice.getResultadoAfip());
        assertNull(invoice.getCae()); assertNull(invoice.getNumero()); assertFalse(invoice.isReceptorPrueba());
        assertFalse(invoice.isComprobanteAfipDisponible()); assertTrue(invoice.getMotivoRechazo().contains("10017"));
        var receipt = service.obtenerComprobante(invoice.getId());
        assertNull(receipt.cae()); assertEquals(1, receipt.tipoComprobante());
        mvc.perform(get("/api/facturas").param("estado", "RECHAZADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].motivoRechazo").value(invoice.getMotivoRechazo()));
    }
    @Test @Transactional void anonymousConsumerFinalUsesClassBAndItsOwnNumberStream() throws Exception {
        var request = request(); request.setClienteId(null);
        var consumer = vat.findAll().stream().filter(v -> v.getCodigoAfip() == 5).findFirst().orElseThrow();
        request.setCondicionIvaId(consumer.getId());
        var invoice = service.crearFactura(request);
        assertEquals("AUTORIZADA", invoice.getEstado()); assertEquals(6, invoice.getTipoComprobanteAfip());
        assertEquals(99, invoice.getReceptorTipoDocumentoAfip()); assertEquals(0, invoice.getReceptorNumeroDocumentoAfip());
        verify(wsfe).obtenerUltimoComprobante(anyString(), anyString(), anyLong(), anyInt(), eq(6));
        service.consultarSiguienteNumeroArca(request.getPuntoVentaId(), vat.findAll().stream().filter(v -> v.getCodigoAfip() == 1).findFirst().orElseThrow().getId());
        verify(wsfe).obtenerUltimoComprobante(anyString(), anyString(), anyLong(), anyInt(), eq(1));
    }
    @Test @Transactional void internalXWorksWithoutArcaAndKeepsItsOwnNumberAndPdf() throws Exception {
        var request = request(); request.setComprobanteInterno(true);
        var client = clients.findById(request.getClienteId()).orElseThrow();
        client.setCuitCuil("123"); // No fiscal identity verification is needed for an internal document.
        long next = service.consultarSiguienteNumeroInterno(request.getPuntoVentaId());
        var first = service.crearFactura(request);
        var second = service.crearFactura(request);
        assertTrue(first.isComprobanteInterno()); assertEquals(next, first.getNumero());
        assertEquals(next + 1, second.getNumero()); assertEquals("EMITIDA", first.getEstado());
        assertNull(first.getCae()); assertNull(first.getResultadoAfip()); assertNull(first.getMotivoRechazo());
        verifyNoInteractions(wsaa, wsfe);
        var receipt = service.obtenerComprobante(first.getId());
        var pdfService = new ComprobanteFacturaService();
        try (var pdf = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(
                new java.io.ByteArrayInputStream(pdfService.generarPdf(receipt))))) {
            String text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdf.getPage(1));
            assertTrue(text.contains("COMPROBANTE X")); assertTrue(text.contains("DOCUMENTO NO VALIDO COMO FACTURA"));
            assertFalse(text.contains("CAE:")); assertFalse(text.contains("VISTA PREVIA"));
            assertNull(pdf.getPage(1).getResources().getResource(com.itextpdf.kernel.pdf.PdfName.XObject));
        }
        assertThrows(IllegalArgumentException.class, () -> pdfService.generarUrlQr(receipt));
        mvc.perform(get("/api/facturas/{id}", first.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobanteInterno").value(true));
        mvc.perform(get("/api/facturas").param("clienteId", request.getClienteId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].comprobanteInterno").value(true));
        mvc.perform(get("/api/facturas/siguiente-numero").param("puntoVentaId", request.getPuntoVentaId().toString())
                .param("comprobanteInterno", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.numero").value(next + 2));
        var excel = mvc.perform(get("/api/facturas/excel").param("clienteId", request.getClienteId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel))) {
            assertTrue(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue().startsWith("X-"));
        }
        request.setNumero(9999L);
        service.actualizarFactura(first.getId(), request);
        assertEquals(next, first.getNumero());
        request.setComprobanteInterno(false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.actualizarFactura(first.getId(), request));
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test @Transactional void cancellationRestoresFiscalOutcomeAndEditingCannotChangeIt() throws Exception {
        var rejectedRequest = request();
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenThrow(new ArcaRechazoException("10017: receptor invalido"));
        var rejected = service.crearFactura(rejectedRequest);
        service.anularFactura(rejected.getId());
        assertEquals("RECHAZADA", rejected.getEstadoAnteriorAnulacion()); assertEquals("ANULADA", rejected.getEstado());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.actualizarFactura(rejected.getId(), rejectedRequest));
        service.reactivarFactura(rejected.getId());
        assertEquals("RECHAZADA", rejected.getEstado()); assertNull(rejected.getFechaBaja()); assertNull(rejected.getEstadoAnteriorAnulacion());
        rejectedRequest.setEstado("RECHAZADA"); rejectedRequest.setObservaciones("Correccion local");
        service.actualizarFactura(rejected.getId(), rejectedRequest);
        assertEquals("RECHAZADA", rejected.getEstado()); assertNull(rejected.getCae());
        rejectedRequest.setEstado("EMITIDA");
        assertThrows(IllegalArgumentException.class, () -> service.actualizarFactura(rejected.getId(), rejectedRequest));
        doReturn(new String[]{"70000000001234", "20300101"}).when(wsfe).solicitarCaeFactura(
                anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class));
        var authorized = service.crearFactura(request());
        service.anularFactura(authorized.getId()); service.reactivarFactura(authorized.getId());
        assertEquals("AUTORIZADA", authorized.getEstado()); assertEquals("70000000001234", authorized.getCae());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.actualizarFactura(authorized.getId(), request()));
        mvc.perform(get("/api/facturas/{id}", authorized.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));
        mvc.perform(get("/api/facturas").param("estado", "NO_EXISTE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/facturas/pdf").param("estado", "NO_EXISTE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/facturas/excel").param("estado", "NO_EXISTE")).andExpect(status().isBadRequest());
    }

    @Test @Transactional void editingAlsoRejectsUnsupportedCurrency() {
        var request = request(); request.setComprobanteInterno(true);
        var invoice = service.crearFactura(request);
        currencies.findById(request.getTipoMonedaId()).orElseThrow().setCodigoAfip("DOL");
        assertThrows(IllegalArgumentException.class, () -> service.actualizarFactura(invoice.getId(), request));
    }

    @Test @Transactional void correctingRejectedRecipientKeepsPdfClassConsistentWithVat() throws Exception {
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenThrow(new ArcaRechazoException("10015: DocNro invalido"));
        var request = request();
        var invoice = service.crearFactura(request);
        assertEquals(1, invoice.getTipoComprobanteAfip());
        request.setEstado("RECHAZADA"); request.setClienteId(null);
        request.setCondicionIvaId(vat.findAll().stream().filter(v -> v.getCodigoAfip() == 5).findFirst().orElseThrow().getId());
        service.actualizarFactura(invoice.getId(), request);
        assertEquals(6, service.obtenerComprobante(invoice.getId()).tipoComprobante());
        assertEquals("RECHAZADA", invoice.getEstado()); assertNull(invoice.getCae());
    }

    @Test @Transactional void staleFormVersionCannotOverwriteANewerSave() throws Exception {
        var request = request(); request.setComprobanteInterno(true);
        var invoice = service.crearFactura(request);
        invoices.flush();
        long oldVersion = invoice.getVersion();
        request.setVersion(oldVersion); request.setObservaciones("Primer cambio");
        service.actualizarFactura(invoice.getId(), request); invoices.flush();
        assertTrue(invoice.getVersion() > oldVersion);
        request.setObservaciones("Cambio obsoleto");
        var failure = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.actualizarFactura(invoice.getId(), request));
        assertEquals(409, failure.getStatusCode().value()); assertEquals("Primer cambio", invoice.getObservaciones());
    }

    @Test void concurrentTransactionsCannotOverwriteEachOther() throws Exception {
        var request = request(); request.setComprobanteInterno(true); request.setClienteId(null);
        request.setCondicionIvaId(vat.findAll().stream().filter(v -> v.getCodigoAfip() == 5).findFirst().orElseThrow().getId());
        // This test owns this internal record; remove it after exercising real commit conflicts.
        var invoice = service.crearFactura(request);
        var first = entityManagers.createEntityManager(); var second = entityManagers.createEntityManager();
        try {
            first.getTransaction().begin(); second.getTransaction().begin();
            var a = first.find(com.example.facturacion.entities.FacturaVenta.class, invoice.getId());
            var b = second.find(com.example.facturacion.entities.FacturaVenta.class, invoice.getId());
            a.setObservaciones("Primera transaccion"); first.getTransaction().commit();
            b.setObservaciones("No debe sobrescribir");
            var failure = assertThrows(jakarta.persistence.RollbackException.class, () -> second.getTransaction().commit());
            Throwable cause = failure;
            while (cause != null && !(cause instanceof jakarta.persistence.OptimisticLockException)) cause = cause.getCause();
            assertNotNull(cause);
            assertEquals("Primera transaccion", invoices.findById(invoice.getId()).orElseThrow().getObservaciones());
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close(); second.close(); invoices.deleteById(invoice.getId());
        }
    }

    @Test @Transactional void correctingFiscalInvoiceCannotPairAnonymousRecipientWithRegisteredVat() throws Exception {
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenThrow(new ArcaRechazoException("10015: DocNro invalido"));
        var request = request(); var invoice = service.crearFactura(request);
        request.setEstado("RECHAZADA"); request.setClienteId(null);
        assertThrows(IllegalArgumentException.class, () -> service.actualizarFactura(invoice.getId(), request));
        assertEquals("RECHAZADA", invoice.getEstado());
    }

    @Test void catalogExposesVatCodesAndOnlyOneEnvironment() throws Exception {
        mvc.perform(get("/api/catalogos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturacion.ambiente").value("HOMOLOGACION"))
                .andExpect(jsonPath("$.facturacion.receptorModo").doesNotExist())
                .andExpect(jsonPath("$.condicionesIva[0].codigoAfip").isNumber());
    }

    private void authorizeAgain() throws Exception {
        doReturn(new String[]{"70000000001234", "20300101"}).when(wsfe)
                .solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class));
    }

    @Test @Transactional void xAmountsDoNotAddFiscalVatAndEditingKeepsDateAndNumber() throws Exception {
        var request = request(); request.setComprobanteInterno(true);
        var x = service.crearFactura(request);
        var number = x.getNumero(); var date = x.getFechaEmision();
        double expected = FacturaService.calcularNeto(1, x.getDetalles().get(0).getPrecioUnitario(), 0);
        assertEquals(expected, x.getImporteTotal()); assertEquals(0, x.getDetalles().get(0).getImporteIva());
        request.setFechaEmision(java.time.LocalDate.now().minusYears(1)); request.setNumero(99999L);
        service.actualizarFactura(x.getId(), request);
        assertEquals(number, x.getNumero()); assertEquals(date, x.getFechaEmision());
        assertEquals(expected, x.getImporteCobrado()); assertEquals(0, x.getImporteSaldo());
        assertEquals(0, x.getDetalles().get(0).getImporteIva()); verifyNoInteractions(wsaa, wsfe);
        var receipt = service.obtenerComprobante(x.getId());
        try (var pdf = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(
                new java.io.ByteArrayInputStream(new ComprobanteFacturaService().generarPdf(receipt))))) {
            var text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdf.getPage(1));
            assertFalse(text.contains("Neto gravado:")); assertFalse(text.contains("IVA contenido:"));
            assertTrue(text.contains("DOCUMENTO NO VALIDO COMO FACTURA"));
        }
    }

    @Test @Transactional void xCreatesANewLinkedFiscalInvoiceWithItsSelectedDateWithoutRewritingTheOriginal() throws Exception {
        var request = request(); request.setComprobanteInterno(true);
        var x = service.crearFactura(request);
        var oldDate = java.time.LocalDate.now().minusDays(2); x.setFechaEmision(oldDate); invoices.flush();
        long oldNumber = x.getNumero(); double oldTotal = x.getImporteTotal();
        request.setComprobanteInterno(false); request.setVersion(x.getVersion()); request.setNumero(88888L); request.setFechaEmision(oldDate);
        var fiscal = service.emitirFiscalDesde(x.getId(), request);
        assertNotEquals(x.getId(), fiscal.getId()); assertEquals("AUTORIZADA", fiscal.getEstado());
        assertEquals(oldDate, fiscal.getFechaEmision()); assertEquals(10001L, fiscal.getNumero());
        assertEquals(x.getId(), fiscal.getOrigenComprobante().getId()); assertEquals(fiscal.getId(), x.getComprobanteFiscalDerivado().getId());
        assertEquals(oldDate, x.getFechaEmision()); assertEquals(oldNumber, x.getNumero()); assertEquals(oldTotal, x.getImporteTotal());
        assertEquals("EMITIDA", x.getEstado()); assertTrue(x.isComprobanteInterno()); assertNull(x.getCae()); assertFalse(x.isEditable());
        double vatAmount = java.math.BigDecimal.valueOf(oldTotal).multiply(new java.math.BigDecimal("0.21")).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
        assertEquals(oldTotal + vatAmount, fiscal.getImporteTotal(), 0.001);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.emitirFiscalDesde(x.getId(), request));
        verify(wsfe, times(1)).solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class));
        mvc.perform(get("/api/facturas/{id}", x.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false)).andExpect(jsonPath("$.puedeEmitirFiscal").value(false))
                .andExpect(jsonPath("$.comprobanteFiscalDerivadoId").value(fiscal.getId()));
        mvc.perform(get("/api/facturas").param("numero", oldNumber + "").param("incluirVinculados", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == " + x.getId() + ")].comprobanteFiscalDerivadoId").value(org.hamcrest.Matchers.hasItem(fiscal.getId().intValue())));
    }

    @Test @Transactional void editingARejectionDoesNotResubmitButExplicitEmissionCreatesANewAttempt() throws Exception {
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenThrow(new ArcaRechazoException("10017: CUIT no registrado"));
        var request = request(); var rejected = service.crearFactura(request);
        var oldDate = java.time.LocalDate.now().minusMonths(1); rejected.setFechaEmision(oldDate); invoices.flush();
        request.setEstado("RECHAZADA"); request.setClienteId(null);
        request.setCondicionIvaId(vat.findAll().stream().filter(v -> v.getCodigoAfip() == 5).findFirst().orElseThrow().getId());
        clearInvocations(wsaa, wsfe);
        service.actualizarFactura(rejected.getId(), request); invoices.flush(); verifyNoInteractions(wsaa, wsfe);
        request.setVersion(rejected.getVersion()); authorizeAgain();
        var fiscal = service.emitirFiscalDesde(rejected.getId(), request);
        assertEquals("AUTORIZADA", fiscal.getEstado()); assertEquals(6, fiscal.getTipoComprobanteAfip());
        assertEquals(java.time.LocalDate.now(), fiscal.getFechaEmision());
        assertEquals("RECHAZADA", rejected.getEstado()); assertEquals("R", rejected.getResultadoAfip());
        assertEquals(oldDate, rejected.getFechaEmision()); assertNull(rejected.getCae()); assertNull(rejected.getNumero());
        assertEquals("10017: CUIT no registrado", rejected.getMotivoRechazo());
        assertEquals(rejected.getId(), fiscal.getOrigenComprobante().getId());
        try (var pdf = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(
                new java.io.ByteArrayInputStream(new ComprobanteFacturaService().generarPdf(service.obtenerComprobante(fiscal.getId())))))) {
            var text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdf.getPage(1));
            assertTrue(text.contains("IVA contenido:")); assertTrue(text.contains("Ley 27.743"));
        }
    }

    @Test @Transactional void uncertainFiscalConversionKeepsEvidenceAndCannotBeSubmittedAgain() throws Exception {
        var request = request(); request.setComprobanteInterno(true); var x = service.crearFactura(request); invoices.flush();
        request.setComprobanteInterno(false); request.setVersion(x.getVersion());
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenThrow(new java.io.IOException("Resultado sin confirmar"));
        var uncertain = service.emitirFiscalDesde(x.getId(), request);
        assertEquals("ERROR_AFIP", uncertain.getEstado()); assertNull(uncertain.getCae()); assertNull(uncertain.getNumero());
        assertFalse(uncertain.isPuedeEmitirFiscal()); assertFalse(x.isPuedeEmitirFiscal());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.emitirFiscalDesde(x.getId(), request));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.emitirFiscalDesde(uncertain.getId(), request));
        verify(wsfe, times(1)).solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class));
    }

    @Test @Transactional void fiscalActionRequiresAValidSourceVersionAndNeverConvertsFiscalToX() throws Exception {
        var request = request(); request.setComprobanteInterno(true); var x = service.crearFactura(request); invoices.flush();
        var mapper = new ObjectMapper();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/facturas/{id}/emitir-fiscal", x.getId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        request.setComprobanteInterno(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/facturas/{id}/emitir-fiscal", x.getId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        request.setVersion(x.getVersion() + 1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/facturas/{id}/emitir-fiscal", x.getId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        verifyNoInteractions(wsaa, wsfe);
        request.setVersion(x.getVersion());
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/facturas/{id}/emitir-fiscal", x.getId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.origenComprobanteId").value(x.getId()))
                .andExpect(jsonPath("$.fechaEmision").value(java.time.LocalDate.now().toString())).andReturn();
        long fiscalId = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        request.setComprobanteInterno(true);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.actualizarFactura(fiscalId, request));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.emitirFiscalDesde(fiscalId, request));
    }

    @Test void simultaneousFiscalActionsForTheSameXContactArcaOnlyOnce() throws Exception {
        var request = request(); request.setComprobanteInterno(true); request.setClienteId(null);
        request.setCondicionIvaId(vat.findAll().stream().filter(v -> v.getCodigoAfip() == 5).findFirst().orElseThrow().getId());
        var x = service.crearFactura(request); request.setComprobanteInterno(false); request.setVersion(x.getVersion());
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.function.Supplier<Long> action = () -> {
            try { start.await(); return service.emitirFiscalDesde(x.getId(), request).getId(); }
            catch (org.springframework.web.server.ResponseStatusException conflict) { assertEquals(409, conflict.getStatusCode().value()); return null; }
            catch (jakarta.persistence.OptimisticLockException | org.springframework.dao.OptimisticLockingFailureException conflict) { return null; }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new RuntimeException(interrupted); }
        };
        var first = java.util.concurrent.CompletableFuture.supplyAsync(action);
        var second = java.util.concurrent.CompletableFuture.supplyAsync(action);
        try {
            start.countDown();
            Long a = first.get(30, java.util.concurrent.TimeUnit.SECONDS), b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null));
            verify(wsfe, times(1)).solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class));
        } finally {
            var manager = entityManagers.createEntityManager();
            try {
                manager.getTransaction().begin();
                var source = manager.find(com.example.facturacion.entities.FacturaVenta.class, x.getId());
                var child = source.getComprobanteFiscalDerivado();
                source.setComprobanteFiscalDerivado(null);
                if (child != null) child.setOrigenComprobante(null);
                manager.flush(); if (child != null) manager.remove(child); manager.remove(source);
                manager.getTransaction().commit();
            } finally { manager.close(); }
        }
    }

    @Test @Transactional void authorizationReplacesXInListAndReportsAndBlocksBothRecords() throws Exception {
        var request = request(); request.setComprobanteInterno(true);
        var x = service.crearFactura(request); invoices.flush();
        var sourceBefore = service.obtenerComprobante(x.getId());
        request.setComprobanteInterno(false); request.setVersion(x.getVersion());
        var fiscal = service.emitirFiscalDesde(x.getId(), request); invoices.flush();
        var fiscalBefore = service.obtenerComprobante(fiscal.getId());
        assertTrue(x.isReemplazadoPorFiscal()); assertFalse(x.isEditable()); assertFalse(fiscal.isEditable());
        assertEquals(sourceBefore, service.obtenerComprobante(x.getId()));
        clearInvocations(wsaa, wsfe);
        var mapper = new ObjectMapper();
        for (long id : List.of(x.getId(), fiscal.getId())) {
            request.setVersion(null); request.setComprobanteInterno(id == x.getId());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/facturas/{id}", id)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
            mvc.perform(get("/api/facturas/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));
        }
        assertEquals(fiscalBefore, service.obtenerComprobante(fiscal.getId())); verifyNoInteractions(wsaa, wsfe);
        var filters = new FacturaFiltrosDTO(); filters.setPuntoVentaId(x.getPuntoVenta().getId());
        assertFalse(service.buscarParaReporte(filters).stream().anyMatch(f -> f.getId().equals(x.getId())));
        assertTrue(service.buscarParaReporte(filters).stream().anyMatch(f -> f.getId().equals(fiscal.getId())));
        filters.setClaseDocumento("X"); filters.setNumero(x.getNumero());
        assertEquals(0, service.listar(filters).getTotalElements());
        filters.setIncluirVinculados(true);
        assertTrue(service.listar(filters).getContent().stream().anyMatch(f -> f.getId().equals(x.getId()) && !f.isEditable()));
        service.anularFactura(fiscal.getId()); service.reactivarFactura(fiscal.getId());
        assertFalse(x.isEditable()); assertFalse(fiscal.isEditable());
    }

    @Test @Transactional void rejectedOrUncertainAttemptKeepsOriginalXVisibleAndEditableWithoutAllowingResubmission() throws Exception {
        for (boolean uncertain : List.of(false, true)) {
            var request = request(); request.setComprobanteInterno(true);
            var x = service.crearFactura(request); invoices.flush();
            var sourceBefore = service.obtenerComprobante(x.getId());
            request.setComprobanteInterno(false); request.setVersion(x.getVersion());
            when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                    .thenThrow(uncertain ? new java.io.IOException("Sin confirmacion") : new ArcaRechazoException("Rechazo confirmado"));
            var fiscal = service.emitirFiscalDesde(x.getId(), request); invoices.flush();
            assertEquals(uncertain ? "ERROR_AFIP" : "RECHAZADA", fiscal.getEstado());
            assertFalse(x.isReemplazadoPorFiscal()); assertTrue(x.isEditable()); assertFalse(x.isPuedeEmitirFiscal());
            assertEquals(sourceBefore, service.obtenerComprobante(x.getId()));
            var filters = new FacturaFiltrosDTO(); filters.setClaseDocumento("X"); filters.setPuntoVentaId(x.getPuntoVenta().getId()); filters.setNumero(x.getNumero());
            assertTrue(service.listar(filters).getContent().stream().anyMatch(f -> f.getId().equals(x.getId()) && f.isEditable()));
            assertTrue(service.buscarParaReporte(filters).stream().anyMatch(f -> f.getId().equals(x.getId())));
            assertEquals(service.listar(filters).getContent().size(), service.listar(filters).getTotalElements());
            request.setComprobanteInterno(true); request.setVersion(x.getVersion());
            service.actualizarFactura(x.getId(), request); invoices.flush();
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.emitirFiscalDesde(x.getId(), request));
        }
    }

    @Test @Transactional void authorizedRetryAlsoReplacesTheOriginalXAndStartupRepairsLegacyChains() throws Exception {
        var request = request(); request.setComprobanteInterno(true); var x = service.crearFactura(request); invoices.flush();
        request.setComprobanteInterno(false); request.setVersion(x.getVersion());
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(java.time.LocalDate.class)))
                .thenThrow(new ArcaRechazoException("Rechazo confirmado"));
        var rejected = service.emitirFiscalDesde(x.getId(), request); invoices.flush();
        assertTrue(x.isEditable());
        authorizeAgain(); request.setVersion(rejected.getVersion());
        var authorized = service.emitirFiscalDesde(rejected.getId(), request); invoices.flush();
        assertTrue(x.isReemplazadoPorFiscal()); assertFalse(x.isEditable());
        assertEquals("AUTORIZADA", authorized.getEstado());
        x.setReemplazadoPorFiscal(false); invoices.flush();
        assertTrue(consultas.marcarXReemplazadosPorAutorizacionesExistentes() >= 1);
        persistenceContext.clear();
        assertTrue(invoices.findById(x.getId()).orElseThrow().isReemplazadoPorFiscal());
        assertFalse(service.obtenerFormulario(x.getId()).getEditable());
    }

    @Test @Transactional void sameXNumberInDifferentBranchesHasDifferentFullReferences() throws Exception {
        var request = request(); request.setComprobanteInterno(true);
        var first = service.crearResumen(request);
        var secondPoint = points.findAll().stream().filter(p -> !p.getId().equals(request.getPuntoVentaId())).findFirst().orElseThrow();
        request.setPuntoVentaId(secondPoint.getId()); var second = service.crearResumen(request);
        var secondEntity = invoices.findById(second.getId()).orElseThrow(); secondEntity.setNumero(first.getNumeroFactura()); invoices.flush();
        second.setNumeroFactura(first.getNumeroFactura());
        assertEquals(first.getNumeroFactura(), second.getNumeroFactura());
        assertNotEquals(first.getReferenciaComprobante(), second.getReferenciaComprobante());
        assertTrue(first.getReferenciaCompleta().contains(first.getPuntoVentaDescripcion()));
        var filters = new FacturaFiltrosDTO(); filters.setPuntoVentaId(secondPoint.getId()); filters.setClaseDocumento("X");
        assertTrue(service.listar(filters).getContent().stream().allMatch(f -> f.getPuntoVentaId().equals(secondPoint.getId()) && f.isComprobanteInterno()));
        filters.setNumero(first.getNumeroFactura()); filters.setSize(1);
        var listed = service.listar(filters).getContent().get(0);
        assertTrue(listed.isNumeroRepetidoOtraSucursal());
        assertEquals("X-" + first.getNumeroFactura() + " (" + secondPoint.getDescripcion() + ")", listed.getReferenciaListado());

    }
}
