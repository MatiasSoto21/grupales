package com.example.facturacion.config;

import com.example.facturacion.FacturacionApplication;
import com.example.facturacion.services.ReceptorArcaPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = FacturacionApplication.class, properties = {
    "debug=false", "facturacion.arca-test.enabled=false", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
class StandardConfigurationTest {
    @Autowired Environment environment;
    @Autowired ResourceLoader resources;
    @Autowired ReceptorArcaPolicy recipient;
    @Autowired EmisorComprobanteProperties issuer;
    @Autowired MockMvc mvc;

    @Test void ordinaryStartupHasOneHomologationFlowWithoutProfileArguments() throws Exception {
        assertEquals(0, environment.getActiveProfiles().length);
        assertFalse(environment.acceptsProfiles(Profiles.of("homologacion-demo")));
        assertFalse(environment.containsProperty("facturacion.arca.receptor-modo"));
        assertTrue(issuer.tieneDatosCompletos());
        assertEquals("HOMOLOGACION", recipient.getAmbiente());
        mvc.perform(get("/api/catalogos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.facturacion.ambiente").value("HOMOLOGACION"))
            .andExpect(jsonPath("$.facturacion.receptorModo").doesNotExist());
    }
}
