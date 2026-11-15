package com.example.facturacion.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ArticuloRepository {
    @PersistenceContext private EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public void asignarNumerosDeCatalogoPendientes() {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(731004002)").getResultList();
        em.createNativeQuery("""
                WITH maximo AS (
                    SELECT COALESCE(MAX(numero_catalogo), 0) AS numero FROM catalogo.articulo
                ), pendientes AS (
                    SELECT a.id, ROW_NUMBER() OVER (ORDER BY CASE WHEN EXISTS (
                        SELECT 1 FROM catalogo.lista_precio_articulo p WHERE p.articulo_id = a.id
                        AND p.precio_venta > 0 AND p.precio_venta <= 1.7976931348623157E308
                    ) THEN 0 ELSE 1 END, a.id) AS posicion
                    FROM catalogo.articulo a WHERE a.numero_catalogo IS NULL
                )
                UPDATE catalogo.articulo a SET numero_catalogo = m.numero + p.posicion
                FROM pendientes p CROSS JOIN maximo m WHERE a.id = p.id
                """).executeUpdate();
    }
}
