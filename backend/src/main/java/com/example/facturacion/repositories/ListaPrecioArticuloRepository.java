package com.example.facturacion.repositories;

import com.example.facturacion.entities.ListaPrecioArticulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListaPrecioArticuloRepository
        extends JpaRepository<ListaPrecioArticulo, Long> {

    Optional<ListaPrecioArticulo> findFirstByArticulo_Id(Long articuloId);
}
