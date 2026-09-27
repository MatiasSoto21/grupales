package com.example.facturacion.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// TODO: Agregar @Entity y @Table
@Entity
@Table(name = "condicion_iva", schema = "catalogo")
public class CondicionIva extends AuditoriaApp {
    // TODO: Configurar @Column(nullable = false) en codigoAfip y denominacion
    @Column(nullable = false)
    private int codigoAfip;

    @Column(nullable = false)
    private String denominacion;

    public int getCodigoAfip() {
        return codigoAfip;
    }

    public void setCodigoAfip(int codigoAfip) {
        this.codigoAfip = codigoAfip;
    }

    public String getDenominacion() {
        return denominacion;
    }

    public void setDenominacion(String denominacion) {
        this.denominacion = denominacion;
    }
}
