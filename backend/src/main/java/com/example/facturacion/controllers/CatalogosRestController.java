package com.example.facturacion.controllers;
import com.example.facturacion.services.CatalogosService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
public class CatalogosRestController {
    private final CatalogosService catalogosService;
    @GetMapping
    public Map<String, Object> obtenerCatalogos() { return catalogosService.obtenerCatalogos(); }
}
