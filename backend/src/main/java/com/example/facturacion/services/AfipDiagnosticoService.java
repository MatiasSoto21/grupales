package com.example.facturacion.services;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

@org.springframework.stereotype.Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "facturacion.arca-test.enabled", havingValue = "true")
public class AfipDiagnosticoService {

    @org.springframework.beans.factory.annotation.Value("${facturacion.arca.clave-path:classpath:privada_test.key}")
    private String keyPath;
    @org.springframework.beans.factory.annotation.Value("${facturacion.arca.certificado-path:classpath:certificado_test.crt}")
    private String certPath;

    @Autowired
    private AfipWsaaService afipWsaaService;

    @Autowired
    private AfipWsfeService afipWsfeService;

    @Autowired
    private com.example.facturacion.config.EmisorComprobanteProperties emisor;

    @org.springframework.beans.factory.annotation.Value("${facturacion.arca.ambiente:HOMOLOGACION}")
    private String ambiente;
    private static final int PTO_VTA = 1;

    public Map<String, Object> emitirFacturaB(int docTipo, long docNro, double importeTotal) {
        if (!"HOMOLOGACION".equalsIgnoreCase(ambiente)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!Double.isFinite(importeTotal) || importeTotal <= 0) {
            throw new IllegalArgumentException("El importe debe ser finito y positivo");
        }
        try {
            long CUIT = emisor.obtenerCuitNumerico();

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

            return response;

        } catch (Exception e) {
            throw new IllegalStateException("No se pudo completar el diagnostico ARCA", e);
        }
    }
}