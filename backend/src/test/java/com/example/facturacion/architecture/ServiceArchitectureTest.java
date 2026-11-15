package com.example.facturacion.architecture;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import static org.junit.jupiter.api.Assertions.*;

class ServiceArchitectureTest {
    @Test
    void serviceClassesDoNotDependOnJpaQueryOrPersistenceApis() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        var services = scanner.findCandidateComponents("com.example.facturacion.services");
        assertFalse(services.isEmpty());
        for (var definition : services) {
            String name = definition.getBeanClassName();
            try (var resource = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                assertNotNull(resource, name);
                String bytecode = new String(resource.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytecode.contains("jakarta/persistence/")
                        || bytecode.contains("org/springframework/data/jpa/"),
                        name + " must delegate queries and persistence to repositories");
            }
        }
    }
    @Test
    void controllersDelegateBusinessAndPersistenceToServices() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.web.bind.annotation.RestController.class));
        var controllers = scanner.findCandidateComponents("com.example.facturacion.controllers");
        assertFalse(controllers.isEmpty());
        for (var definition : controllers) {
            String name = definition.getBeanClassName();
            try (var resource = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                assertNotNull(resource, name);
                String bytecode = new String(resource.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytecode.contains("jakarta/persistence/") || bytecode.contains("org/springframework/data/jpa/")
                        || bytecode.contains("com/example/facturacion/repositories/") || bytecode.contains("com/example/facturacion/entities/"),
                        name + " must use services and DTOs");
            }
        }
    }
}
