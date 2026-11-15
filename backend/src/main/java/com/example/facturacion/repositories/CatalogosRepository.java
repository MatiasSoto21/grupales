package com.example.facturacion.repositories;
import com.example.facturacion.entities.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
@Repository
public class CatalogosRepository {
    @PersistenceContext private EntityManager em;
    public List<String> estadosHistoricos() { return em.createQuery("SELECT DISTINCT f.estado FROM FacturaVenta f WHERE f.estado IN ('PENDIENTE', 'PAGADA') ORDER BY f.estado", String.class).getResultList(); }
    public List<CondicionIva> condiciones() { return em.createQuery("SELECT c FROM CondicionIva c ORDER BY c.denominacion", CondicionIva.class).getResultList(); }
    public List<TipoMoneda> monedas() { return em.createQuery("SELECT m FROM TipoMoneda m ORDER BY m.denominacion", TipoMoneda.class).getResultList(); }
    public List<PuntoVenta> puntos() { return em.createQuery("SELECT p FROM PuntoVenta p ORDER BY p.numero", PuntoVenta.class).getResultList(); }
    public List<ListaPrecio> listas() { return em.createQuery("SELECT l FROM ListaPrecio l ORDER BY l.denominacion, l.id", ListaPrecio.class).getResultList(); }
}
