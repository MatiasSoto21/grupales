package com.example.facturacion.runner;

import com.example.facturacion.services.AfipWsaaService;
import com.example.facturacion.services.AfipWsfeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

//@Component
public class AfipTestRunner implements CommandLineRunner {

    @Autowired
    private AfipWsaaService afipWsaaService;

    @Autowired
    private AfipWsfeService afipWsfeService;

    private static final long CUIT = 20303658441L;
    private static final int PTO_VTA = 1;

    @Override
    public void run(String... args) {
        System.out.println("==================================================");
        System.out.println(">>> INICIANDO PRUEBA COMPLETA WSAA + WSFE ARCA <<<");
        System.out.println("==================================================");

        try {
            String keyPath = new File("src/main/resources/privada_test.key").getAbsolutePath();
            String certPath = new File("src/main/resources/certificado_test.crt").getAbsolutePath();

            // 1. Obtener Token y Sign
            String[] auth = afipWsaaService.obtenerTokenYSign(keyPath, certPath);
            String token = auth[0];
            String sign = auth[1];

            // 2. Consultar el último comprobante emitido para Factura B (Tipo 6)
            int ultimoNro = afipWsfeService.obtenerUltimoComprobante(token, sign, CUIT, PTO_VTA, 6);
            int proximoNro = ultimoNro + 1;

            System.out.println("📊 Último comprobante registrado en ARCA: " + ultimoNro);
            System.out.println("🚀 Emitiendo comprobante N°: " + proximoNro);

            // 3. Emitir Factura B por $1210.00 a Consumidor Final (DocTipo 99, DocNro 0)
            String[] resultado = afipWsfeService.solicitarCaeFacturaB(
                    token, sign, CUIT, PTO_VTA, proximoNro, 99, 0, 1210.00, 5
            );

            System.out.println("==================================================");
            System.out.println("🎉 ¡FACTURA EMITIDA EXITOSAMENTE EN ARCA!");
            System.out.println("NÚMERO COMPROBANTE: " + resultado[2]);
            System.out.println("CAE AUTORIZADO:    " + resultado[0]);
            System.out.println("VENCIMIENTO CAE:   " + resultado[1]);
            System.out.println("==================================================");

        } catch (Throwable e) {
            System.err.println("❌ ERROR DURANTE LA FACTURACIÓN:");
            e.printStackTrace();
            System.out.println("==================================================");
        }
    }
}