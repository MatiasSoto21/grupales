package com.example.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacturaRequestDTO {

    private Long numero;
    private LocalDate fechaEmision;

    private Long clienteId;
    private Long condicionIvaId;
    private Long tipoMonedaId;
    private Long puntoVentaId;

    private double importeCobrado;
    private double importeSaldo;

    private String estado;
    private String observaciones;

    private List<FacturaDetalleRequestDTO> detalles;
}
