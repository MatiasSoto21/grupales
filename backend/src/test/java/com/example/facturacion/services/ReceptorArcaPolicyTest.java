package com.example.facturacion.services;
import com.example.facturacion.entities.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class ReceptorArcaPolicyTest {
    private CondicionIva iva(int code) {
        var iva = new CondicionIva(); iva.setCodigoAfip(code); iva.setDenominacion("Condicion " + code); return iva;
    }
    @Test void selectedClientIsAlwaysDeclaredEvenWithFictionalCuit() {
        var client = new Cliente(); client.setCuitCuil("00-12345678-9"); client.setDenominacion("Cliente inventado");
        var receptor = new ReceptorArcaPolicy("HOMOLOGACION").resolver(client, iva(1));
        assertEquals(80, receptor.tipoDocumento()); assertEquals(123456789L, receptor.numeroDocumento());
        assertEquals(1, receptor.condicionIvaId()); assertEquals("00123456789", receptor.cuitCuil()); assertFalse(receptor.prueba());
    }
    @Test void anonymousConsumerFinalIsExplicitAndCannotBecomeRegistered() {
        var policy = new ReceptorArcaPolicy("HOMOLOGACION");
        var receptor = policy.resolver(null, iva(5));
        assertEquals(99, receptor.tipoDocumento()); assertEquals(0, receptor.numeroDocumento());
        assertThrows(IllegalArgumentException.class, () -> policy.resolver(null, iva(1)));
        var client = new Cliente(); client.setCuitCuil("123");
        assertThrows(IllegalArgumentException.class, () -> policy.resolver(client, iva(5)));
    }
    @Test void productionIsForbidden() {
        assertThrows(ResponseStatusException.class, () -> new ReceptorArcaPolicy("PRODUCCION").resolver(null, iva(5)));
    }
    @Test void receiptClassMatchesVatCondition() {
        for (int code : new int[]{1,6,13,16}) assertEquals(1, ReceptorArcaPolicy.tipoComprobante(code));
        for (int code : new int[]{4,5,7,8,9,10,15}) assertEquals(6, ReceptorArcaPolicy.tipoComprobante(code));
        assertThrows(IllegalArgumentException.class, () -> ReceptorArcaPolicy.tipoComprobante(99));
    }
}
