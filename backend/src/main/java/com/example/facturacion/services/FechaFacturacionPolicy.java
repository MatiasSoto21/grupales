package com.example.facturacion.services;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FechaFacturacionPolicy {
    private final Clock clock;

    public LocalDate hoy() { return LocalDate.now(clock); }
    public Instant ahora() { return clock.instant(); }
    public LocalDate fechaFiscalMinima() { return hoy().minusDays(5); }

    public void validar(LocalDate operacion, LocalDate emision) {
        LocalDate hoy = hoy();
        if (operacion == null || emision == null) throw new IllegalArgumentException("Las fechas de operacion y comprobante son obligatorias");
        if (operacion.isAfter(hoy)) throw new IllegalArgumentException("La fecha de operacion no puede ser futura");
        if (emision.isAfter(hoy) || emision.isBefore(hoy.minusDays(5)))
            throw new IllegalArgumentException("Para productos, la fecha fiscal debe estar entre " + hoy.minusDays(5) + " y " + hoy);
        if (emision.isBefore(operacion)) throw new IllegalArgumentException("La fecha fiscal no puede ser anterior a la operacion registrada");
        if (!YearMonth.from(operacion).equals(YearMonth.from(emision)))
            throw new IllegalArgumentException("La operacion y el comprobante deben corresponder al mismo mes; revise el periodo antes de emitir");
    }

    public void validarContraUltimoAutorizado(LocalDate emision, LocalDate ultimaFecha) {
        if (ultimaFecha == null) throw new IllegalStateException("ARCA no confirmo la fecha del ultimo comprobante autorizado");
        if (emision.isBefore(ultimaFecha)) throw new IllegalArgumentException(
                "La fecha fiscal " + emision + " es anterior a la ultima autorizada por ARCA (" + ultimaFecha + ") para ese punto de venta y clase");
    }
}
