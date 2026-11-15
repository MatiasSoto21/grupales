package com.example.facturacion.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmisorComprobantePropertiesTest {

    @Test
    void missingIssuerDataReturnsServiceUnavailable() {
        EmisorComprobanteProperties properties = new EmisorComprobanteProperties();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                properties::validar);

        assertEquals(503, exception.getStatusCode().value());
    }

    @Test
    void validFormattedCuitIsConvertedToNumericValue() {
        EmisorComprobanteProperties properties = new EmisorComprobanteProperties();
        properties.setDenominacion("Emisor de prueba");
        properties.setCuit("20-30365844-1");
        properties.setCondicionIva("Responsable Inscripto");
        properties.setDomicilio("San Martín 123");

        assertEquals(20303658441L, properties.obtenerCuitNumerico());
    }

    @Test
    void missingReceiptDisplayDataDoesNotPreventArcaCuitLookup() {
        EmisorComprobanteProperties properties = new EmisorComprobanteProperties();
        properties.setCuit("20-30365844-1");

        assertEquals(20303658441L, properties.obtenerCuitNumerico());
        org.junit.jupiter.api.Assertions.assertFalse(properties.tieneDatosCompletos());
    }
}
