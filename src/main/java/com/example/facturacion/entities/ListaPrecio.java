package com.example.facturacion.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// TODO: Agregar @Entity y @Table
@Entity
@Table(name = "lista_precio", schema = "catalogo")
public class ListaPrecio extends AuditoriaApp {
    // TODO: Configurar @Column(nullable = false) en codigo y denominacion
    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String denominacion;

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDenominacion() {
        return denominacion;
    }

    public void setDenominacion(String denominacion) {
        this.denominacion = denominacion;
    }
}
