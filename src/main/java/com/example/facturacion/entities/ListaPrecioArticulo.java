package com.example.facturacion.entities;

import jakarta.persistence.*;

// TODO: Agregar @Entity y @Table
@Entity
@Table(name = "lista_precio_articulo", schema = "catalogo")
public class ListaPrecioArticulo extends AuditoriaApp {
    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = false)
    @ManyToOne
    @JoinColumn(name = "lista_precio_id", nullable = false)
    private ListaPrecio listaPrecio;

    // TODO: Configurar @Column(nullable = false)
    @Column(nullable = false)
    private double precioVenta;

    // TODO: Configurar @ManyToOne y @JoinColumn(nullable = false)
    @ManyToOne
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;

    public ListaPrecio getListaPrecio() {
        return listaPrecio;
    }

    public void setListaPrecio(ListaPrecio listaPrecio) {
        this.listaPrecio = listaPrecio;
    }

    public double getPrecioVenta() {
        return precioVenta;
    }

    public void setPrecioVenta(double precioVenta) {
        this.precioVenta = precioVenta;
    }

    public Articulo getArticulo() {
        return articulo;
    }

    public void setArticulo(Articulo articulo) {
        this.articulo = articulo;
    }
}