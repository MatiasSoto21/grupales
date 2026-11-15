package com.example.facturacion.controllers;

import com.example.facturacion.dto.ArticuloBusquedaDTO;
import com.example.facturacion.dto.PageResponseDTO;
import com.example.facturacion.services.ArticuloService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/articulos")
@RequiredArgsConstructor
public class ArticuloRestController {
    private final ArticuloService articulos;

    @GetMapping
    public PageResponseDTO<ArticuloBusquedaDTO> buscar(
            @RequestParam Long listaPrecioId,
            @RequestParam(required = false) String denominacion,
            @RequestParam(required = false) String codigo,
            @RequestParam(defaultValue = "parcial") String coincidencia,
            @RequestParam(defaultValue = "denominacion") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return articulos.buscar(listaPrecioId, denominacion, codigo, coincidencia, sortBy, sortDir, page, size);
    }

    @GetMapping("/precios/{id}")
    public ArticuloBusquedaDTO precio(@PathVariable Long id) {
        return articulos.obtenerPrecio(id);
    }
}
