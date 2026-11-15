package com.example.facturacion.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FechaFacturacionConfig {
    @Bean
    public Clock facturacionClock() {
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
