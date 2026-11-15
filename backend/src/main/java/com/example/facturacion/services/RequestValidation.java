package com.example.facturacion.services;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class RequestValidation {
    private final Validator validator;
    public RequestValidation(Validator validator) { this.validator = validator; }

    public void validate(Object request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .sorted(java.util.Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; ")));
        }
    }
}
