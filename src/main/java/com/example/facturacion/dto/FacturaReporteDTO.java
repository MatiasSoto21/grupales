package com.example.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FacturaReporteDTO {

    private Long numeroFactura;
    private LocalDate fechaEmision;
    private String clienteDenominacion;
    private String condicionIva;
    private String puntoVentaDescripcion;
    private double importeTotal;
    private long cantidadItems;
}
