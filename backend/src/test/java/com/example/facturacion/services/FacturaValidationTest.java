package com.example.facturacion.services;

import com.example.facturacion.entities.FacturaVenta;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FacturaValidationTest {
    @Test
    void decimalHalfUpAvoidsBinaryFloatingPointRoundingErrors() {
        assertEquals(1.01, FacturaService.calcularNeto(1, 1.005, 0));
        assertEquals(90.0, FacturaService.calcularNeto(3, 33.33, 9.99));
        assertEquals(0, FacturaService.calcularNeto(2, 100, 100));
    }

    @Test
    void realAuthorizationCannotBeEditedWhenIssuerDisplayDataIsMissing() {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setCae("70000000001234");
        invoice.setResultadoAfip("A");
        invoice.setComprobanteAfipDisponible(false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> FacturaService.validarFacturaEditable(invoice));
    }

    @Test void reactivationHandlesLegacyRecordsWithoutLosingFiscalEvidence() {
        var invoice = new FacturaVenta(); invoice.setEstado("ANULADA");
        invoice.setResultadoAfip("R"); assertEquals("RECHAZADA", invoice.estadoAlReactivar());
        invoice.setResultadoAfip(null); invoice.setMotivoRechazo("Respuesta de ARCA incompleta");
        assertEquals("ERROR_AFIP", invoice.estadoAlReactivar());
        invoice.setEstadoAnteriorAnulacion("ERROR_AFIP"); assertEquals("ERROR_AFIP", invoice.estadoAlReactivar());
        invoice.setCae("70000000001234"); assertEquals("AUTORIZADA", invoice.estadoAlReactivar());
        assertFalse(invoice.isEditable());
    }

    @Test void incompleteAuthorizationAndUnknownExternalOutcomeAreReadOnly() {
        var invoice = new FacturaVenta(); invoice.setCae("70000000001234");
        assertFalse(invoice.isEditable());
        invoice.setCae(null); invoice.setEstado("ERROR_AFIP"); assertFalse(invoice.isEditable());
        invoice.setEstado("RECHAZADA"); assertTrue(invoice.isEditable());
        invoice.setEstado("ANULADA"); assertFalse(invoice.isEditable());
    }

    @Test
    void filtersAcceptInclusiveBoundsAndRejectNegativeOrInfiniteValues() {
        FacturaService.validarFiltros(null, null, 0.0, null, false, 10L, 10L, 10L);
        assertThrows(IllegalArgumentException.class, () ->
                FacturaService.validarFiltros(null, null, Double.POSITIVE_INFINITY, null, false, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                FacturaService.validarFiltros(null, null, null, null, false, -1L, null, null));
    }
}
