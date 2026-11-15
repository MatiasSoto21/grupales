package com.example.facturacion.controllers;
import com.example.facturacion.services.AfipDiagnosticoService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/afip")
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "facturacion.arca-test.enabled", havingValue = "true")
public class AfipController {
    private final AfipDiagnosticoService diagnostico;
    @PostMapping("/emitir-factura-b")
    public Map<String, Object> emitirFacturaB(@RequestParam(defaultValue = "99") int docTipo,
            @RequestParam(defaultValue = "0") long docNro, @RequestParam double importeTotal) {
        return diagnostico.emitirFacturaB(docTipo, docNro, importeTotal);
    }
}
