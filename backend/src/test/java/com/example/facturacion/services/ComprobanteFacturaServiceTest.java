package com.example.facturacion.services;

import com.example.facturacion.dto.FacturaComprobanteDTO;
import com.example.facturacion.entities.FacturaVenta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComprobanteFacturaServiceTest {

    private final ComprobanteFacturaService service = new ComprobanteFacturaService();

    @Test
    void qrContainsArcaVerificationFieldsForCustomerInvoice() throws Exception {
        String url = service.generarUrlQr(comprobante());
        String encodedPayload = URLDecoder.decode(
                url.substring(url.indexOf("?p=") + 3),
                StandardCharsets.UTF_8);
        JsonNode payload = new ObjectMapper().readTree(
                Base64.getDecoder().decode(encodedPayload));

        assertEquals(1, payload.get("ver").asInt());
        assertEquals("2026-11-12", payload.get("fecha").asText());
        assertEquals(20303658441L, payload.get("cuit").asLong());
        assertEquals(1, payload.get("ptoVta").asInt());
        assertEquals(6, payload.get("tipoCmp").asInt());
        assertEquals(1045, payload.get("nroCmp").asInt());
        assertEquals(80, payload.get("tipoDocRec").asInt());
        assertEquals(20123456789L, payload.get("nroDocRec").asLong());
        assertEquals("E", payload.get("tipoCodAut").asText());
        assertEquals(70000000001234L, payload.get("codAut").asLong());
    }

    @Test
    void generatedReceiptIsAReadablePdfDocument() {
        byte[] pdf = service.generarPdf(comprobante());

        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertTrue(pdf.length > 1000);
    }

    @Test
    void invoicePreviewWithoutCaeCanBeGeneratedWithoutQr() {
        FacturaComprobanteDTO preview = new FacturaComprobanteDTO(
                2L,
                "HOMOLOGACION",
                "Emisor de prueba",
                "20303658441",
                "Responsable Inscripto",
                "San Martín 123",
                "Consumidor Final",
                null,
                null,
                99,
                0L,
                "Consumidor Final",
                1,
                "San Martín 123",
                6,
                1046L,
                LocalDate.of(2026, 11, 12),
                List.of(),
                0,
                0,
                0,
                "PES",
                1,
                null,
                null,
                "ERROR_AFIP", false);

        byte[] pdf = service.generarPdf(preview);

        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertTrue(pdf.length > 1000);
    }

    @Test
    void editingAnInvoiceWithAnAuthorizedReceiptIsRejected() {
        FacturaVenta factura = new FacturaVenta();
        factura.setCae("70000000001234");
        factura.setComprobanteAfipDisponible(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> FacturaService.validarFacturaEditable(factura));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void unverifiedStoredCaeRequiresReviewBeforeEditing() {
        FacturaVenta factura = new FacturaVenta();
        factura.setCae("70000000001234");

        assertFalse(factura.isComprobanteAfipDisponible());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> FacturaService.validarFacturaEditable(factura));
    }

    private FacturaComprobanteDTO comprobante() {
        return new FacturaComprobanteDTO(
                1L,
                "HOMOLOGACION",
                "Emisor de prueba",
                "20303658441",
                "Responsable Inscripto",
                "San Martín 123",
                "Cliente de prueba",
                "20123456789",
                "San Martín 123",
                80,
                20123456789L,
                "Responsable Inscripto",
                1,
                "San Martín 123",
                6,
                1045L,
                LocalDate.of(2026, 11, 12),
                List.of(new FacturaComprobanteDTO.Detalle(
                        "Artículo de prueba", 1, 100, 0, 100, 21, 121)),
                100,
                21,
                121,
                "PES",
                1,
                "70000000001234",
                LocalDate.of(2026, 11, 22),
                "AUTORIZADA", false);
    }
}
