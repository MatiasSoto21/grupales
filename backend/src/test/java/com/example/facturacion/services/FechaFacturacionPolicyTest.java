package com.example.facturacion.services;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FechaFacturacionPolicyTest {
    private final ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
    private FechaFacturacionPolicy at(String instant) {
        return new FechaFacturacionPolicy(Clock.fixed(Instant.parse(instant), zone));
    }

    @Test void thursdayAndSaturdayDatesAreBothValidForSaturdaySubmission() {
        var policy = at("2026-11-14T15:00:00Z");
        var thursday = LocalDate.of(2026, 11, 12); var saturday = LocalDate.of(2026, 11, 14);
        assertDoesNotThrow(() -> policy.validar(thursday, thursday));
        assertDoesNotThrow(() -> policy.validar(saturday, saturday));
        assertDoesNotThrow(() -> policy.validar(thursday, saturday));
        assertEquals(LocalDate.of(2026, 11, 9), policy.fechaFiscalMinima());
    }

    @Test void dateWindowAndCalendarConsistencyAreValidated() {
        var policy = at("2026-11-14T15:00:00Z");
        assertDoesNotThrow(() -> policy.validar(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9)));
        assertThrows(IllegalArgumentException.class, () -> policy.validar(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 8)));
        assertThrows(IllegalArgumentException.class, () -> policy.validar(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)));
        assertThrows(IllegalArgumentException.class, () -> policy.validar(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 12)));
        assertThrows(IllegalArgumentException.class, () -> policy.validar(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 14)));
        assertThrows(IllegalArgumentException.class, () -> policy.validar(null, null));
        var novemberThird = at("2026-11-03T15:00:00Z");
        assertDoesNotThrow(() -> novemberThird.validar(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31)));
    }

    @Test void fiscalDateCannotPrecedeTheLastAuthorizedInTheSameSeries() {
        var policy = at("2026-11-14T15:00:00Z"); var friday = LocalDate.of(2026, 11, 13);
        assertThrows(IllegalArgumentException.class, () -> policy.validarContraUltimoAutorizado(friday.minusDays(1), friday));
        assertDoesNotThrow(() -> policy.validarContraUltimoAutorizado(friday, friday));
        assertThrows(IllegalStateException.class, () -> policy.validarContraUltimoAutorizado(friday, null));
    }

    @Test void argentinaCalendarDoesNotChangeAtUtcMidnight() {
        assertEquals(LocalDate.of(2026, 11, 13), at("2026-11-14T01:00:00Z").hoy());
    }
}
