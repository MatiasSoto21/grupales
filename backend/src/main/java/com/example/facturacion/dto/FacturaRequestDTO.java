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

    @jakarta.validation.constraints.PositiveOrZero
    private Long numero;
    private LocalDate fechaEmision;
    private LocalDate fechaOperacion;
    private java.time.Instant fechaSolicitudAfip;
    private boolean comprobanteInterno;
    private Boolean editable;
    private Boolean puedeEmitirFiscal;
    private String referenciaComprobante;
    private String comprobanteFiscalReferencia;
    private Long origenComprobanteId;
    private Long comprobanteFiscalDerivadoId;
    @jakarta.validation.constraints.PositiveOrZero
    private Long version;

    public FacturaRequestDTO(Long numero, LocalDate fechaEmision, Long clienteId, Long condicionIvaId,
            Long tipoMonedaId, Long puntoVentaId, String estado, String observaciones,
            List<FacturaDetalleRequestDTO> detalles) {
        this.numero = numero; this.fechaEmision = fechaEmision; this.clienteId = clienteId;
        this.condicionIvaId = condicionIvaId; this.tipoMonedaId = tipoMonedaId; this.puntoVentaId = puntoVentaId;
        this.estado = estado; this.observaciones = observaciones; this.detalles = detalles;
    }

    @jakarta.validation.constraints.Positive
    private Long clienteId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Long condicionIvaId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Long tipoMonedaId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Long puntoVentaId;


    @jakarta.validation.constraints.Size(max = 30)
    private String estado;
    @jakarta.validation.constraints.Size(max = 500)
    private String observaciones;

    @jakarta.validation.constraints.NotEmpty
    private List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid FacturaDetalleRequestDTO> detalles;
}
