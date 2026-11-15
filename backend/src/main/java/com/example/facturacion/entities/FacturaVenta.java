package com.example.facturacion.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "factura_venta", schema = "ventas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"detalles", "origenComprobante", "comprobanteFiscalDerivado"})
@ToString(exclude = {"detalles", "origenComprobante", "comprobanteFiscalDerivado"})
public class FacturaVenta extends AuditoriaApp {

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origen_comprobante_id", unique = true)
    private FacturaVenta origenComprobante;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_fiscal_derivado_id", unique = true)
    private FacturaVenta comprobanteFiscalDerivado;

    @Transient
    public boolean isPuedeEmitirFiscal() {
        return comprobanteFiscalDerivado == null && isEditable() && ((isComprobanteInterno() && "EMITIDA".equals(estado))
                || (!isComprobanteInterno() && "RECHAZADA".equals(estado)
                    && "R".equals(resultadoAfip) && numero == null));
    }

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reemplazadoPorFiscal;

    private Long numero;

    @Column(nullable = false)
    private LocalDate fechaEmision;
    private LocalDate fechaOperacion;
    private java.time.Instant fechaSolicitudAfip;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;

    @ManyToOne
    @JoinColumn(name = "tipo_moneda_id", nullable = false)
    private TipoMoneda tipoMoneda;

    @ManyToOne
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;

    private double importeCobrado;
    private double importeSaldo;

    @Column(nullable = false)
    private double importeTotal;

    private String cae;
    private LocalDate caeFechaVencimiento;
    private String resultadoAfip;
    private String motivoRechazo;
    private String ambienteAfip;
    private String emisorDenominacionAfip;
    private String emisorCuitAfip;
    private String emisorCondicionIvaAfip;
    private String emisorDomicilioAfip;
    private String receptorDenominacionAfip;
    private String receptorCuitCuilAfip;
    private String receptorDomicilioAfip;
    private Integer receptorTipoDocumentoAfip;
    private Long receptorNumeroDocumentoAfip;
    private String receptorCondicionIvaAfip;
    private Integer puntoVentaNumeroAfip;
    private String puntoVentaDomicilioAfip;
    // 0 is an application-only marker for X, never a WSFE receipt code.
    private Integer tipoComprobanteAfip;

    @Transient
    public boolean isComprobanteInterno() { return Integer.valueOf(0).equals(tipoComprobanteAfip); }
    private String codigoMonedaAfip;
    private Double cotizacionMonedaAfip;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean comprobanteAfipDisponible;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean receptorPrueba;

    @Column(nullable = false)
    private String estado;
    private String estadoAnteriorAnulacion;

    @Transient
    public boolean isEditable() {
        return !reemplazadoPorFiscal && (isComprobanteInterno() || comprobanteFiscalDerivado == null) && (cae == null || cae.isBlank()) && !"A".equals(resultadoAfip)
                && !comprobanteAfipDisponible && !java.util.Set.of("AUTORIZADA", "ERROR_AFIP", "ANULADA").contains(estado == null ? "" : estado);
    }

    @Transient
    public String estadoAlReactivar() {
        if ((cae != null && !cae.isBlank()) || "A".equals(resultadoAfip) || comprobanteAfipDisponible)
            return "AUTORIZADA";
        if ("R".equals(resultadoAfip)) return "RECHAZADA";
        if (estadoAnteriorAnulacion != null && !estadoAnteriorAnulacion.isBlank()
                && !"ANULADA".equals(estadoAnteriorAnulacion)) return estadoAnteriorAnulacion;
        if (!isComprobanteInterno() && motivoRechazo != null && !motivoRechazo.isBlank()) return "ERROR_AFIP";
        return "EMITIDA";
    }

    private LocalDate fechaAnulacion;
    @Column(length = 500)
    private String observaciones;

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FacturaVentaDetalle> detalles = new ArrayList<>();

    public void addDetalle(FacturaVentaDetalle detalle) {
        detalles.add(detalle);
        detalle.setFactura(this);
    }
}
