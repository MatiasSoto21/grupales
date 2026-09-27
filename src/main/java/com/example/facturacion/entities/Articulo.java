package com.example.facturacion.entities;

import jakarta.persistence.*;

// TODO: Agregar @Entity y @Table
@Entity
@Table(name = "articulo", schema = "catalogo")
public class Articulo extends AuditoriaApp {
    // TODO: Configurar @ManyToOne
    @ManyToOne
    @JoinColumn(name = "rubro_id")
    private Rubro rubro;

    // TODO: Configurar @Column(nullable = false) en codigo y denominacion
    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String denominacion;

    // TODO: Configurar @ManyToOne
    @ManyToOne
    @JoinColumn(name = "marca_id")
    private Marca marca;

    public Rubro getRubro() {
        return rubro;
    }

    public void setRubro(Rubro rubro) {
        this.rubro = rubro;
    }

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

    public Marca getMarca() {
        return marca;
    }

    public void setMarca(Marca marca) {
        this.marca = marca;
    }
}
