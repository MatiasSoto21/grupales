package com.example.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacturaDetalleRequestDTO {

    private Long articuloId;
    private String descripcion;
    private double cantidad;
    private double porcentajeBonificacion;
}
