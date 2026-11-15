package com.example.facturacion.services;

import com.example.facturacion.entities.Cliente;
import com.example.facturacion.entities.CondicionIva;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@Component
public class ReceptorArcaPolicy {
    private final String ambiente;

    public ReceptorArcaPolicy(@Value("${facturacion.arca.ambiente:HOMOLOGACION}") String ambiente) {
        this.ambiente = ambiente.trim().toUpperCase(Locale.ROOT);
    }

    public String getAmbiente() { return ambiente; }

    public static int tipoComprobante(int codigoIva) {
        return switch (codigoIva) {
            case 1, 6, 13, 16 -> 1;
            case 4, 5, 7, 8, 9, 10, 15 -> 6;
            default -> throw new IllegalArgumentException("Condicion de IVA no admitida por WSFE");
        };
    }

    public Receptor resolver(Cliente cliente, CondicionIva condicion) {
        if (!"HOMOLOGACION".equals(ambiente)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La emision esta restringida a HOMOLOGACION");
        }
        tipoComprobante(condicion.getCodigoAfip());
        var documento = FacturaService.obtenerReceptorAfip(cliente);
        if (cliente == null && condicion.getCodigoAfip() != 5) {
            throw new IllegalArgumentException("Sin cliente, la condicion fiscal del receptor debe ser Consumidor Final");
        }
        return new Receptor(documento.tipoDocumento(), documento.numeroDocumento(), condicion.getCodigoAfip(),
                cliente == null ? "Consumidor Final" : cliente.getDenominacion(),
                cliente == null ? null : cliente.getCuitCuil().replaceAll("[^0-9]", ""), condicion.getDenominacion(), false);
    }

    public record Receptor(int tipoDocumento, long numeroDocumento, int condicionIvaId,
            String denominacion, String cuitCuil, String condicionIva, boolean prueba) { }
}
