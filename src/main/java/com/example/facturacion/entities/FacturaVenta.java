package com.example.facturacion.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.List;

// TODO: Agregar @Entity y @Table
@Entity
@Table(name = "factura_venta", schema = "ventas")
public class FacturaVenta extends AuditoriaApp {
    private Long numero;
    // TODO: Configurar @Column(nullable = false)
    @Column(nullable = false)
    private LocalDate fechaEmision;


    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = true)
    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = false)
    @ManyToOne
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;

    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = false)
    @ManyToOne
    @JoinColumn(name = "tipo_moneda_id", nullable = false)
    private TipoMoneda tipoMoneda;


    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = false)
    @ManyToOne
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;

    private double importeCobrado;
    private double importeSaldo;

    // TODO: Configurar @Column(nullable = false)
    @Column(nullable = false)
    private double importeTotal;

    private String cae;
    private LocalDate caeFechaVencimiento;
    private String resultadoAfip;
    private String motivoRechazo;

    // TODO: Configurar @Column(nullable = false)
    @Column(nullable = false)
    private String estado;

    private LocalDate fechaAnulacion;
    private String observaciones;

    // TODO: Configurar @OneToMany(mappedBy = "factura", cascade =
    //CascadeType.ALL)
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL)
    private List<FacturaVentaDetalle> detalles;



    public void addDetalle(FacturaVentaDetalle detalle){
        detalles.add(detalle);
        detalle.setFactura(this);
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public CondicionIva getCondicionIva() {
        return condicionIva;
    }

    public void setCondicionIva(CondicionIva condicionIva) {
        this.condicionIva = condicionIva;
    }

    public TipoMoneda getTipoMoneda() {
        return tipoMoneda;
    }

    public void setTipoMoneda(TipoMoneda tipoMoneda) {
        this.tipoMoneda = tipoMoneda;
    }

    public Long getNumero() {
        return numero;
    }

    public void setNumero(Long numero) {
        this.numero = numero;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public PuntoVenta getPuntoVenta() {
        return puntoVenta;
    }

    public void setPuntoVenta(PuntoVenta puntoVenta) {
        this.puntoVenta = puntoVenta;
    }

    public double getImporteCobrado() {
        return importeCobrado;
    }

    public void setImporteCobrado(double importeCobrado) {
        this.importeCobrado = importeCobrado;
    }

    public double getImporteSaldo() {
        return importeSaldo;
    }

    public void setImporteSaldo(double importeSaldo) {
        this.importeSaldo = importeSaldo;
    }

    public double getImporteTotal() {
        return importeTotal;
    }

    public void setImporteTotal(double importeTotal) {
        this.importeTotal = importeTotal;
    }

    public String getCae() {
        return cae;
    }

    public void setCae(String cae) {
        this.cae = cae;
    }

    public LocalDate getCaeFechaVencimiento() {
        return caeFechaVencimiento;
    }

    public void setCaeFechaVencimiento(LocalDate caeFechaVencimiento) {
        this.caeFechaVencimiento = caeFechaVencimiento;
    }

    public String getResultadoAfip() {
        return resultadoAfip;
    }

    public void setResultadoAfip(String resultadoAfip) {
        this.resultadoAfip = resultadoAfip;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getFechaAnulacion() {
        return fechaAnulacion;
    }

    public void setFechaAnulacion(LocalDate fechaAnulacion) {
        this.fechaAnulacion = fechaAnulacion;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public List<FacturaVentaDetalle> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<FacturaVentaDetalle> detalles) {
        this.detalles = detalles;
    }
}
