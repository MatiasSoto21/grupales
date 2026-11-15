package com.example.facturacion.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class BloqueoTransaccionalRepository {
    @PersistenceContext
    private EntityManager em;

    public void bloquearFlujo(String flujo) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:flujo, 0))")
                .setParameter("flujo", flujo).getResultList();
    }

    public void bloquearCuit(long cuit) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:cuit)")
                .setParameter("cuit", cuit).getResultList();
    }
}
