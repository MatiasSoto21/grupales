package com.example.facturacion.runner;
import com.example.facturacion.services.AfipDiagnosticoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "facturacion.arca-test.enabled", havingValue = "true")
public class AfipTestRunner implements CommandLineRunner {
    private final AfipDiagnosticoService diagnostico;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AfipTestRunner.class);
    @Override public void run(String... args) {
        try { log.info("Resultado del diagnostico ARCA: {}", diagnostico.emitirFacturaB(99, 0, 1210.00)); }
        catch (Exception error) { log.error("No se pudo completar el diagnostico ARCA", error); }
    }
}
