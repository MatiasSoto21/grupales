package com.example.facturacion.services;

import com.example.facturacion.FacturacionApplication;
import com.example.facturacion.dto.*;
import com.example.facturacion.entities.FacturaVenta;
import com.example.facturacion.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = FacturacionApplication.class, properties = {
    "facturacion.datos-iniciales.emitir-arca=false",
    "debug=false", "facturacion.arca-test.enabled=false", "logging.level.root=WARN",
    "facturacion.comprobante.emisor.denominacion=Emisor de prueba", "facturacion.comprobante.emisor.condicion-iva=Responsable Inscripto",
    "facturacion.comprobante.emisor.domicilio=Domicilio de prueba 123"
})
@AutoConfigureMockMvc
@Transactional
class FechaFiscalIntegrationTest {
    static final LocalDate THURSDAY = LocalDate.of(2026, 11, 12);
    static final LocalDate SATURDAY = LocalDate.of(2026, 11, 14);
    static final Instant SUBMITTED = Instant.parse("2026-11-14T15:00:00Z");
    @MockBean Clock clock;
    @MockBean AfipWsaaService wsaa;
    @MockBean AfipWsfeService wsfe;
    @Autowired FacturaService service;
    @Autowired FacturaVentaRepository invoices;
    @Autowired CondicionIvaRepository taxes;
    @Autowired TipoMonedaRepository currencies;
    @Autowired PuntoVentaRepository points;
    @Autowired ListaPrecioArticuloRepository prices;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @BeforeEach void setup() throws Exception {
        when(clock.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(clock.instant()).thenReturn(SUBMITTED);
        when(wsaa.obtenerTokenYSign(anyString(), anyString())).thenReturn(new String[]{"token", "sign"});
        when(wsfe.obtenerUltimoComprobante(anyString(), anyString(), anyLong(), anyInt(), anyInt())).thenReturn(500);
        when(wsfe.obtenerFechaComprobanteAutorizado(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(THURSDAY);
        when(wsfe.solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(LocalDate.class)))
                .thenReturn(new String[]{"70000000001234", "20300101"});
    }

    private FacturaRequestDTO request() {
        var price = prices.findAll().get(0);
        var tax = taxes.findAll().stream().filter(t -> t.getCodigoAfip() == 5).findFirst().orElseThrow();
        var point = points.findAll().stream().filter(p -> p.getNumero() == 1).findFirst().orElseThrow();
        var currency = currencies.findAll().stream().filter(c -> "PES".equals(c.getCodigoAfip())).findFirst().orElseThrow();
        return new FacturaRequestDTO(null, null, null, tax.getId(), currency.getId(), point.getId(), "EMITIDA", "Fechas de prueba",
                List.of(new FacturaDetalleRequestDTO(price.getArticulo().getId(), price.getId(), "Producto", 1, 0)));
    }

    private FacturaVenta thursdayX(FacturaRequestDTO request) {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-12T15:00:00Z"));
        request.setComprobanteInterno(true); var source = service.crearFactura(request); invoices.flush();
        when(clock.instant()).thenReturn(SUBMITTED);
        request.setComprobanteInterno(false); request.setVersion(source.getVersion());
        request.setFechaOperacion(THURSDAY); request.setFechaEmision(THURSDAY);
        return source;
    }

    @Test void thursdaySaleSentSaturdayKeepsThursdayInDatabaseSoapInputPdfAndQr() throws Exception {
        var request = request(); var source = thursdayX(request); var number = source.getNumero();
        var fiscal = service.emitirFiscalDesde(source.getId(), request);
        assertEquals(THURSDAY, fiscal.getFechaEmision()); assertEquals(THURSDAY, fiscal.getFechaOperacion());
        assertEquals(SUBMITTED, fiscal.getFechaSolicitudAfip()); assertEquals(THURSDAY, source.getFechaEmision());
        assertEquals(number, source.getNumero()); assertNull(source.getFechaSolicitudAfip());
        verify(wsfe).solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), eq(THURSDAY));
        mvc.perform(get("/api/facturas/{id}", fiscal.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaEmision").value("2026-11-12"))
                .andExpect(jsonPath("$.fechaOperacion").value("2026-11-12"))
                .andExpect(jsonPath("$.fechaSolicitudAfip").value(SUBMITTED.toString()));
        var receipt = service.obtenerComprobante(fiscal.getId());
        var pdfService = new ComprobanteFacturaService();
        String url = pdfService.generarUrlQr(receipt);
        String payload = java.net.URLDecoder.decode(url.substring(url.indexOf("?p=") + 3), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("2026-11-12", mapper.readTree(java.util.Base64.getDecoder().decode(payload)).get("fecha").asText());
        try (var pdf = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(new java.io.ByteArrayInputStream(pdfService.generarPdf(receipt))))) {
            assertTrue(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdf.getPage(1)).contains("12/11/2026"));
        }
    }

    @Test void thursdayBudgetCanBecomeSaturdaySaleWithoutChangingTheX() {
        var request = request(); var source = thursdayX(request);
        request.setFechaOperacion(SATURDAY); request.setFechaEmision(SATURDAY);
        var fiscal = service.emitirFiscalDesde(source.getId(), request);
        assertEquals(SATURDAY, fiscal.getFechaEmision()); assertEquals(SATURDAY, fiscal.getFechaOperacion());
        assertEquals(THURSDAY, source.getFechaEmision()); assertEquals(SUBMITTED, fiscal.getFechaSolicitudAfip());
    }

    @Test void laterAuthorizedDateBlocksTheOldFiscalDateWithoutSilentlyReplacingIt() throws Exception {
        var request = request(); var source = thursdayX(request);
        when(wsfe.obtenerFechaComprobanteAutorizado(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(THURSDAY.plusDays(1));
        mvc.perform(post("/api/facturas/{id}/emitir-fiscal", source.getId()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ultima autorizada")));
        verify(wsfe, never()).solicitarCaeFactura(anyString(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(LocalDate.class));
        assertNull(source.getComprobanteFiscalDerivado()); assertEquals(THURSDAY, source.getFechaEmision());
        request.setFechaEmision(SATURDAY);
        var fiscal = service.emitirFiscalDesde(source.getId(), request);
        assertEquals(THURSDAY, fiscal.getFechaOperacion()); assertEquals(SATURDAY, fiscal.getFechaEmision());
    }

    @Test void invalidDatesAreRejectedBeforeAuthenticationAndDoNotCreateAttempts() throws Exception {
        var request = request(); var source = thursdayX(request); long count = invoices.count();
        for (LocalDate invalid : List.of(SATURDAY.minusDays(6), SATURDAY.plusDays(1))) {
            request.setFechaOperacion(invalid); request.setFechaEmision(invalid);
            mvc.perform(post("/api/facturas/{id}/emitir-fiscal", source.getId()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        }
        assertEquals(count, invoices.count()); assertNull(source.getComprobanteFiscalDerivado()); verifyNoInteractions(wsaa, wsfe);
    }

    @Test void editingTheXPreservesItsDatesAndDoesNotSendAnything() {
        var request = request(); var source = thursdayX(request);
        request.setComprobanteInterno(true); request.setFechaEmision(SATURDAY); request.setFechaOperacion(SATURDAY);
        service.actualizarFactura(source.getId(), request);
        assertEquals(THURSDAY, source.getFechaEmision()); assertNull(source.getFechaOperacion());
        assertNull(source.getFechaSolicitudAfip()); verifyNoInteractions(wsaa, wsfe);
    }

    @Test void fiscalCalendarMetadataUsesServerDateAndArgentinaTimezone() throws Exception {
        mvc.perform(get("/api/catalogos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturacion.fechaActual").value("2026-11-14"))
                .andExpect(jsonPath("$.facturacion.fechaFiscalMinima").value("2026-11-09"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T01:00:00Z"));
        assertEquals(THURSDAY.plusDays(1), service.crearFactura(request()).getFechaEmision());
    }
}
