package com.example.facturacion.dto;

import com.example.facturacion.entities.ListaPrecioArticulo;

public record ArticuloBusquedaDTO(Long listaPrecioArticuloId, Long articuloId, String codigo,
        String denominacion, Long listaPrecioId, String listaPrecioDenominacion, double precioVenta) {
    public static ArticuloBusquedaDTO from(ListaPrecioArticulo precio) {
        return new ArticuloBusquedaDTO(precio.getId(), precio.getArticulo().getId(),
                precio.getArticulo().getNumeroCatalogo().toString(), precio.getArticulo().getDenominacion(),
                precio.getListaPrecio().getId(), precio.getListaPrecio().getDenominacion(), precio.getPrecioVenta());
    }
}
