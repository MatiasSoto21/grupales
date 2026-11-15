package com.example.facturacion.services;

import com.example.facturacion.dto.ArticuloBusquedaDTO;
import com.example.facturacion.dto.PageResponseDTO;
import com.example.facturacion.repositories.ListaPrecioArticuloRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticuloService {
    private final ListaPrecioArticuloRepository precios;


    public ArticuloBusquedaDTO obtenerPrecio(Long id) {
        return ArticuloBusquedaDTO.from(precios.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el precio indicado")));
    }

    public PageResponseDTO<ArticuloBusquedaDTO> buscar(Long listaPrecioId, String denominacion, String codigo,
            String coincidencia, String sortBy, String sortDir, int page, int size) {
        if (listaPrecioId == null || listaPrecioId < 1 || page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / Math.max(size, 1)
                || !Set.of("denominacion", "codigo", "precioVenta").contains(sortBy) || !Set.of("asc", "desc").contains(sortDir)
                || !Set.of("parcial", "exacta").contains(coincidencia)
                || (denominacion != null && denominacion.length() > 255)
                || (codigo != null && codigo.length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtros de articulos invalidos");
        }
        var result = precios.buscarFiltrados(listaPrecioId, denominacion, codigo, coincidencia, sortBy, sortDir, page, size);
        return new PageResponseDTO<>(result.getContent().stream().map(ArticuloBusquedaDTO::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }
}
