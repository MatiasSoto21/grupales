package com.example.facturacion.entities;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDate;


// TODO: Agregar anotación @MappedSuperclass
@MappedSuperclass
public abstract class AuditoriaApp extends EntityId {
    // TODO: Configurar @Column(nullable = false) y formato de fecha
    @Column(nullable = false)
    protected LocalDate fechaAlta;

    protected LocalDate fechaBaja;

    // TODO: Configurar @Column(nullable = false) y formato de fecha
    @Column(nullable = false)
    protected LocalDate fechaModificacion;

    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = false)
    @ManyToOne
    @JoinColumn(name = "usuario_carga_id", nullable = false)
    protected Usuario usuarioCarga;

    // TODO: Configurar @ManyToOne
    @ManyToOne
    @JoinColumn(name = "usuario_baja_id")
    protected Usuario usuarioBaja;

    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = false)
    @ManyToOne
    @JoinColumn(name = "usuario_modificacion_id", nullable = false)
    protected Usuario usuarioModificacion;

    public LocalDate getFechaAlta() {
        return fechaAlta;
    }

    public void setFechaAlta(LocalDate fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    public LocalDate getFechaBaja() {
        return fechaBaja;
    }

    public void setFechaBaja(LocalDate fechaBaja) {
        this.fechaBaja = fechaBaja;
    }

    public LocalDate getFechaModificacion() {
        return fechaModificacion;
    }

    public void setFechaModificacion(LocalDate fechaModificacion) {
        this.fechaModificacion = fechaModificacion;
    }

    public Usuario getUsuarioCarga() {
        return usuarioCarga;
    }

    public void setUsuarioCarga(Usuario usuarioCarga) {
        this.usuarioCarga = usuarioCarga;
    }

    public Usuario getUsuarioBaja() {
        return usuarioBaja;
    }

    public void setUsuarioBaja(Usuario usuarioBaja) {
        this.usuarioBaja = usuarioBaja;
    }

    public Usuario getUsuarioModificacion() {
        return usuarioModificacion;
    }

    public void setUsuarioModificacion(Usuario usuarioModificacion) {
        this.usuarioModificacion = usuarioModificacion;
    }
}
