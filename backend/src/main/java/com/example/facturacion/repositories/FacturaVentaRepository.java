package com.example.facturacion.repositories;

import com.example.facturacion.entities.FacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {
    @Query("SELECT f.id FROM FacturaVenta f WHERE f.estado = 'EMITIDA' AND f.resultadoAfip = 'SIMULADO' "
            + "AND f.observaciones = 'Factura de prueba' AND f.numero BETWEEN 1001 AND 1050 "
            + "AND f.cae IS NULL AND f.tipoComprobanteAfip IS NULL AND f.fechaSolicitudAfip IS NULL ORDER BY f.id")
    java.util.List<Long> buscarMuestrasInicialesSinEmitir();
    Optional<FacturaVenta> findTopByOrderByNumeroDesc();

    @Query("SELECT COALESCE(MAX(f.numero), 0) FROM FacturaVenta f")
    Long obtenerNumeroMaximo();

    boolean existsByNumero(Long numero);

    @Query("SELECT MAX(f.numero) FROM FacturaVenta f WHERE f.tipoComprobanteAfip = 0 AND f.puntoVenta.id = :punto")
    Long obtenerUltimoNumeroInterno(@org.springframework.data.repository.query.Param("punto") Long puntoVentaId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FacturaVenta f WHERE f.id = :id")
    Optional<FacturaVenta> buscarPorIdConBloqueo(@org.springframework.data.repository.query.Param("id") Long id);

}
