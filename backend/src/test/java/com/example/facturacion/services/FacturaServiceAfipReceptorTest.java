package com.example.facturacion.services;

import com.example.facturacion.entities.Cliente;
import com.example.facturacion.entities.CondicionIva;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FacturaServiceAfipReceptorTest {

    @Test
    void consumidorFinalUsesDocumentTypeWithoutNumber() {
        assertEquals(
                new FacturaService.ReceptorAfip(99, 0L),
                FacturaService.obtenerReceptorAfip(null));
    }

    @Test
    void clientCuitIsSentToArcaAsType80WithDigitsOnly() {
        Cliente cliente = new Cliente();
        cliente.setCuitCuil("20-12345678-9");

        assertEquals(
                new FacturaService.ReceptorAfip(80, 20123456789L),
                FacturaService.obtenerReceptorAfip(cliente));
    }

    @Test
    void clientWithoutAnElevenDigitCuitCannotBeSentToArca() {
        Cliente cliente = new Cliente();
        cliente.setCuitCuil("12345678");

        assertThrows(
                IllegalArgumentException.class,
                () -> FacturaService.obtenerReceptorAfip(cliente));
    }

    @Test
    void firstInvoiceStoresVatConditionOnExistingClient() {
        Cliente cliente = new Cliente();
        CondicionIva seleccionada = condicionIva(1L);

        assertSame(seleccionada, FacturaService.aplicarCondicionIvaCliente(cliente, seleccionada));
        assertSame(seleccionada, cliente.getCondicionIva());
    }

    @Test
    void registeredClientVatConditionIsUsedForInvoice() {
        Cliente cliente = new Cliente();
        CondicionIva registrada = condicionIva(1L);
        CondicionIva seleccionada = condicionIva(1L);
        cliente.setCondicionIva(registrada);

        assertSame(registrada, FacturaService.aplicarCondicionIvaCliente(cliente, seleccionada));
    }

    @Test
    void differentVatConditionIsRejectedForRegisteredClient() {
        Cliente cliente = new Cliente();
        cliente.setCondicionIva(condicionIva(1L));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> FacturaService.aplicarCondicionIvaCliente(cliente, condicionIva(2L)));

        assertEquals(400, exception.getStatusCode().value());
    }

    private CondicionIva condicionIva(Long id) {
        CondicionIva condicionIva = new CondicionIva();
        condicionIva.setId(id);
        return condicionIva;
    }
}
