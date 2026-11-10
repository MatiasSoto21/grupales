package com.example.facturacion.controllers;

import com.example.facturacion.services.AfipWsaaService;
import com.example.facturacion.services.AfipWsfeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/afip")
@CrossOrigin(origins = "*")
public class AfipController {

    @Autowired
    private AfipWsaaService afipWsaaService;

    @Autowired
    private AfipWsfeService afipWsfeService;

    private static final long CUIT = 20303658441L;
    private static final int PTO_VTA = 1;

    @PostMapping("/emitir-factura-b")
    public ResponseEntity<?> emitirFacturaB(@RequestParam(defaultValue = "99") int docTipo,
                                            @RequestParam(defaultValue = "0") long docNro,
                                            @RequestParam double importeTotal) {
        try {
            String keyPath = new File("src/main/resources/privada_test.key").getAbsolutePath();
            String certPath = new File("src/main/resources/certificado_test.crt").getAbsolutePath();

            String[] auth = afipWsaaService.obtenerTokenYSign(keyPath, certPath);
            int ultimoNro = afipWsfeService.obtenerUltimoComprobante(auth[0], auth[1], CUIT, PTO_VTA, 6);
            int proximoNro = ultimoNro + 1;

            String[] res = afipWsfeService.solicitarCaeFacturaB(
                    auth[0], auth[1], CUIT, PTO_VTA, proximoNro, docTipo, docNro, importeTotal, 5
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "OK");
            response.put("cae", res[0]);
            response.put("vencimientoCae", res[1]);
            response.put("numeroComprobante", res[2]);
            response.put("puntoVenta", PTO_VTA);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("mensaje", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}