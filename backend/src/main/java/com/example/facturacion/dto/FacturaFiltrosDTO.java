package com.example.facturacion.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter @Setter
public class FacturaFiltrosDTO {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fechaDesde;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fechaHasta;
    private String estado;
    private Double montoMinimo;
    private Long clienteId;
    private boolean sinCliente;
    private Long numero;
    private Long numeroDesde;
    private Long numeroHasta;
    private Long puntoVentaId;
    private String claseDocumento;
    private boolean incluirVinculados;
    private int page;
    private int size = 10;
    private String sortBy = "fechaEmision";
    private String sortDir = "desc";

    public static FacturaFiltrosDTO desde(LocalDate desde, LocalDate hasta, String estado, Double minimo,
            Long cliente, boolean sinCliente, Long numero, Long inicio, Long fin, int page, int size, String campo, String direccion) {
        var f = new FacturaFiltrosDTO(); f.fechaDesde = desde; f.fechaHasta = hasta; f.estado = estado; f.montoMinimo = minimo;
        f.clienteId = cliente; f.sinCliente = sinCliente; f.numero = numero; f.numeroDesde = inicio; f.numeroHasta = fin;
        f.page = page; f.size = size; f.sortBy = campo; f.sortDir = direccion; return f;
    }
}
