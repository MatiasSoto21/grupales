package com.example.facturacion;

import com.example.facturacion.entities.FacturaVenta;
import com.example.facturacion.repositories.FacturaVentaRepository;
import com.example.facturacion.services.FacturaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmisionMuestrasInicialesRunnerTest {
    private final FacturaVentaRepository repository = mock(FacturaVentaRepository.class);
    private final FacturaService service = mock(FacturaService.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(EmisionMuestrasInicialesRunner.class)
            .withInitializer(c -> {
                c.getBeanFactory().registerSingleton("facturasDePrueba", repository);
                c.getBeanFactory().registerSingleton("servicioDePrueba", service);
            });

    @Test void ordinaryStartupEnablesInitialSubmission() {
        context.run(c -> assertThat(c).hasSingleBean(EmisionMuestrasInicialesRunner.class));
    }

    @Test void testsAndExplicitlyDisabledStartupNeverSubmit() {
        context.withPropertyValues("facturacion.datos-iniciales.emitir-arca=false")
                .run(c -> assertThat(c).doesNotHaveBean(EmisionMuestrasInicialesRunner.class));
        verifyNoInteractions(service);
    }

    @Test void rejectionIsPersistedAndRemainingExamplesAreProcessed() {
        when(repository.buscarMuestrasInicialesSinEmitir()).thenReturn(List.of(1L, 2L));
        when(service.emitirMuestraInicial(1L)).thenReturn(invoice("RECHAZADA"));
        when(service.emitirMuestraInicial(2L)).thenReturn(invoice("AUTORIZADA"));
        new EmisionMuestrasInicialesRunner(repository, service).run();
        var order = inOrder(service);
        order.verify(service).emitirMuestraInicial(1L);
        order.verify(service).emitirMuestraInicial(2L);
        verifyNoMoreInteractions(service);
    }

    @Test void uncertainResultStopsBatchWithoutRetrying() {
        when(repository.buscarMuestrasInicialesSinEmitir()).thenReturn(List.of(1L, 2L));
        when(service.emitirMuestraInicial(1L)).thenReturn(invoice("ERROR_AFIP"));
        new EmisionMuestrasInicialesRunner(repository, service).run();
        verify(service).emitirMuestraInicial(1L);
        verifyNoMoreInteractions(service);
    }

    @Test void laterStartupDoesNotResubmitProcessedExamples() {
        when(repository.buscarMuestrasInicialesSinEmitir()).thenReturn(List.of());
        new EmisionMuestrasInicialesRunner(repository, service).run();
        verifyNoInteractions(service);
    }

    private FacturaVenta invoice(String state) {
        var invoice = new FacturaVenta(); invoice.setEstado(state); return invoice;
    }
}
