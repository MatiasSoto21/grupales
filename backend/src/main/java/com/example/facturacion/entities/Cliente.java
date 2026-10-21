package com.example.facturacion.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cliente", schema = "ventas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class Cliente extends AuditoriaApp {

    @Column(nullable = false)
    private String cuitCuil;

    @Column(nullable = false)
    private String denominacion;

    @OneToOne
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @OneToOne
    @JoinColumn(name = "domicilio_id", nullable = false)
    private Domicilio domicilio;
}
