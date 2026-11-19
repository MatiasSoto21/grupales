package com.example.facturacion;

import com.example.facturacion.repositories.FacturaVentaRepository;
import com.example.facturacion.services.FacturaService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Submit pending initial examples once, committing each real ARCA result separately. */
@Component
@Order(1)
@ConditionalOnProperty(name = "facturacion.datos-iniciales.emitir-arca", havingValue = "true", matchIfMissing = true)
public class EmisionMuestrasInicialesRunner implements CommandLineRunner {
    private final FacturaVentaRepository facturas;
    private final FacturaService servicio;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmisionMuestrasInicialesRunner.class);

    public EmisionMuestrasInicialesRunner(FacturaVentaRepository facturas, FacturaService servicio) {
        this.facturas = facturas;
        this.servicio = servicio;
    }

    @Override
    public void run(String... args) {
        for (Long id : facturas.buscarMuestrasInicialesSinEmitir()) {
            var factura = servicio.emitirMuestraInicial(id);
            log.info("Muestra inicial {}: {}", id, factura.getEstado());
            if ("ERROR_AFIP".equals(factura.getEstado())) {
                log.warn("Carga inicial detenida ante resultado incierto. No se reintenta la factura {}.", id);
                break;
            }
        }
    }
}
