package com.example.facturacion.repositories;

import com.example.facturacion.entities.FacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {
}
