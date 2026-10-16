package com.example.facturacion.repositories;

import com.example.facturacion.entities.PuntoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PuntoVentaRepository
        extends JpaRepository<PuntoVenta, Long> {
}
