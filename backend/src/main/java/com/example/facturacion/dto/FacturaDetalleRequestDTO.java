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

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Long articuloId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Long listaPrecioArticuloId;
    @jakarta.validation.constraints.Size(max = 255)
    private String descripcion;
    private double cantidad;
    private double porcentajeBonificacion;
}
