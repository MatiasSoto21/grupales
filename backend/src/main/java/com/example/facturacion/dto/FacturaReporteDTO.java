
package com.example.facturacion.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class FacturaReporteDTO {

    private Long id;
    private Long numeroFactura;
    private LocalDate fechaEmision;
    private String clienteDenominacion;
    private String condicionIva;
    private String puntoVentaDescripcion;
    private double importeTotal;
    private long cantidadItems;
    private String estado;
    private boolean tieneCae;
    private boolean comprobanteDisponible;
    private boolean editable = true;
    private boolean receptorPrueba;
    private String motivoRechazo;
    private boolean comprobanteInterno;
    private Long puntoVentaId;
    private Integer puntoVentaNumero;
    private Integer tipoComprobanteAfip;
    private Long origenInternoId;
    private String origenInternoReferencia;
    private boolean origenInternoEditable;
    private boolean numeroRepetidoOtraSucursal;

    public String getReferenciaListado() {
        if (numeroFactura == null) return "Intento #" + id;
        String numero = (comprobanteInterno ? "X-" : "") + numeroFactura;
        if (!numeroRepetidoOtraSucursal) return numero;
        String sucursal = puntoVentaDescripcion == null || puntoVentaDescripcion.isBlank()
                ? "Punto de venta " + puntoVentaNumero : puntoVentaDescripcion;
        return numero + " (" + sucursal + ")";
    }


    public String getReferenciaComprobante() {
        String clase = comprobanteInterno ? "X" : Integer.valueOf(1).equals(tipoComprobanteAfip) ? "A"
                : Integer.valueOf(6).equals(tipoComprobanteAfip) ? "B" : "Fiscal";
        if (numeroFactura == null) return clase + " / intento #" + id;
        return clase + "-" + (puntoVentaNumero == null ? "" : String.format(java.util.Locale.ROOT, "%05d-", puntoVentaNumero))
                + String.format(java.util.Locale.ROOT, "%08d", numeroFactura);
    }
    public String getReferenciaCompleta() { return getReferenciaComprobante() + " (" + puntoVentaDescripcion + ")"; }

    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision,
            String clienteDenominacion, String condicionIva, String puntoVentaDescripcion,
            double importeTotal, long cantidadItems, String estado, boolean tieneCae,
            boolean comprobanteDisponible, boolean editable, boolean receptorPrueba, String motivoRechazo,
            boolean comprobanteInterno, Long origenComprobanteId, Long comprobanteFiscalDerivadoId,
            Long puntoVentaId, Integer puntoVentaNumero, Integer tipoComprobanteAfip) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion,
                importeTotal, cantidadItems, estado, tieneCae, comprobanteDisponible, editable, receptorPrueba,
                motivoRechazo, comprobanteInterno, origenComprobanteId, comprobanteFiscalDerivadoId);
        this.puntoVentaId = puntoVentaId; this.puntoVentaNumero = puntoVentaNumero; this.tipoComprobanteAfip = tipoComprobanteAfip;
    }

    private Long origenComprobanteId;
    private Long comprobanteFiscalDerivadoId;

    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision,
            String clienteDenominacion, String condicionIva, String puntoVentaDescripcion,
            double importeTotal, long cantidadItems, String estado, boolean tieneCae,
            boolean comprobanteDisponible, boolean editable, boolean receptorPrueba, String motivoRechazo,
            boolean comprobanteInterno, Long origenComprobanteId, Long comprobanteFiscalDerivadoId) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion,
                importeTotal, cantidadItems, estado, tieneCae, comprobanteDisponible, editable,
                receptorPrueba, motivoRechazo, comprobanteInterno);
        this.origenComprobanteId = origenComprobanteId;
        this.comprobanteFiscalDerivadoId = comprobanteFiscalDerivadoId;
    }


    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision,
            String clienteDenominacion, String condicionIva, String puntoVentaDescripcion,
            double importeTotal, long cantidadItems, String estado, boolean tieneCae,
            boolean comprobanteDisponible, boolean editable, boolean receptorPrueba, String motivoRechazo,
            boolean comprobanteInterno) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion,
                importeTotal, cantidadItems, estado, tieneCae, comprobanteDisponible, editable, receptorPrueba, motivoRechazo);
        this.comprobanteInterno = comprobanteInterno;
    }

    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision,
            String clienteDenominacion, String condicionIva, String puntoVentaDescripcion,
            double importeTotal, long cantidadItems, String estado, boolean tieneCae,
            boolean comprobanteDisponible, boolean editable, boolean receptorPrueba, String motivoRechazo) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion,
                importeTotal, cantidadItems, estado, tieneCae, comprobanteDisponible, editable);
        this.receptorPrueba = receptorPrueba;
        this.motivoRechazo = motivoRechazo;
    }

    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision,
            String clienteDenominacion, String condicionIva, String puntoVentaDescripcion,
            double importeTotal, long cantidadItems, String estado, boolean tieneCae,
            boolean comprobanteDisponible, boolean editable) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion,
                importeTotal, cantidadItems, estado, tieneCae, comprobanteDisponible);
        this.editable = editable && !tieneCae && !comprobanteDisponible
                && !java.util.Set.of("AUTORIZADA", "ERROR_AFIP", "ANULADA").contains(estado == null ? "" : estado);
    }

    public FacturaReporteDTO(
            Long id,
            Long numeroFactura,
            LocalDate fechaEmision,
            String clienteDenominacion,
            String condicionIva,
            String puntoVentaDescripcion,
            double importeTotal,
            long cantidadItems,
            String estado,
            boolean tieneCae,
            boolean comprobanteDisponible) {
        this.id = id;
        this.numeroFactura = numeroFactura;
        this.fechaEmision = fechaEmision;
        this.clienteDenominacion = clienteDenominacion;
        this.condicionIva = condicionIva;
        this.puntoVentaDescripcion = puntoVentaDescripcion;
        this.importeTotal = importeTotal;
        this.cantidadItems = cantidadItems;
        this.estado = estado;
        this.tieneCae = tieneCae;
        this.comprobanteDisponible = comprobanteDisponible;
        this.editable = !tieneCae && !comprobanteDisponible
                && !java.util.Set.of("AUTORIZADA", "ERROR_AFIP", "ANULADA").contains(estado == null ? "" : estado);
    }

    public FacturaReporteDTO(
            Long id,
            Long numeroFactura,
            LocalDate fechaEmision,
            String clienteDenominacion,
            String condicionIva,
            String puntoVentaDescripcion,
            double importeTotal,
            long cantidadItems,
            String estado) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva,
                puntoVentaDescripcion, importeTotal, cantidadItems, estado, false, false);
    }
}
