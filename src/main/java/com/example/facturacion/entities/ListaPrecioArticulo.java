package com.example.facturacion.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lista_precio_articulo", schema = "catalogo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class ListaPrecioArticulo extends AuditoriaApp {

    @ManyToOne
    @JoinColumn(name = "lista_precio_id", nullable = false)
    private ListaPrecio listaPrecio;

    @Column(nullable = false)
    private double precioVenta;

    @ManyToOne
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
}
