package com.example.facturacion.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// TODO: Agregar @Entity y @Table
@Entity
@Table(name = "contacto", schema = "ventas")
public class Contacto extends EntityId {
    private String email;
    private String telefono;
    private String celular;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }
}
