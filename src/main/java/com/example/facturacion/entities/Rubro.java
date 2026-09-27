package com.example.facturacion.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// TODO: Agregar @Entity y @Table
@Entity
@Table(name = "rubro", schema = "catalogo")
public class Rubro extends AuditoriaApp {
    // TODO: Configurar @Column(nullable = false) en denominacion y codigo
    @Column(nullable = false)
    private String denominacion;

    @Column(nullable = false)
    private Integer codigo;

    public String getDenominacion() {
        return denominacion;
    }

    public void setDenominacion(String denominacion) {
        this.denominacion = denominacion;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }
}