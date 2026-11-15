package com.example.facturacion.dto;

import com.example.facturacion.entities.Cliente;

public record ClienteBusquedaDTO(Long id, String denominacion, String cuitCuil,
        Long condicionIvaId, String condicionIvaDenominacion) {
    public static ClienteBusquedaDTO from(Cliente cliente) {
        var iva = cliente.getCondicionIva();
        return new ClienteBusquedaDTO(cliente.getId(), cliente.getDenominacion(), cliente.getCuitCuil(),
                iva == null ? null : iva.getId(), iva == null ? null : iva.getDenominacion());
    }
}
