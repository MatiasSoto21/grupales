package com.example.facturacion.repositories;

import com.example.facturacion.entities.FacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {
    Optional<FacturaVenta> findTopByOrderByNumeroDesc();

    @Query("SELECT COALESCE(MAX(f.numero), 0) FROM FacturaVenta f")
    Long obtenerNumeroMaximo();

    boolean existsByNumero(Long numero);
}
