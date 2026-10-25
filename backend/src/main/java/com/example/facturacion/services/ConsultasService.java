package com.example.facturacion.services;

import com.example.facturacion.entities.*;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.List;

public class ConsultasService {

    // =========================================================================
    // NIVEL 1: Consultas Básicas y Proyecciones
    // =========================================================================

    // 1. Obtener la lista completa de todas las facturas de venta registradas.
    public static List<FacturaVenta> obtenerTodasLasFacturas(EntityManager em) {
        String jpql = "SELECT f FROM FacturaVenta f";
        return em.createQuery(jpql, FacturaVenta.class).getResultList();
    }

    // 2. Seleccionar únicamente el número de factura, la fecha de emisión y el importe total.
    public static List<Object[]> obtenerResumenFacturas(EntityManager em) {
        String jpql = "SELECT f.numero, f.fechaEmision, f.importeTotal FROM FacturaVenta f";
        return em.createQuery(jpql, Object[].class).getResultList();
    }

    // 3. Obtener todos los artículos que pertenecen a un rubro con una denominación específica.
    public static List<Articulo> obtenerArticulosPorRubro(EntityManager em, String denominacionRubro) {
        String jpql = "SELECT a FROM Articulo a WHERE a.rubro.denominacion = :rubroNombre";
        return em.createQuery(jpql, Articulo.class)
                .setParameter("rubroNombre", denominacionRubro)
                 .getResultList();
    }

    // 4. Listar todas las facturas de venta emitidas dentro de un rango de fechas.
    public static List<FacturaVenta> obtenerFacturasPorRangoFechas(EntityManager em, LocalDate desde, LocalDate hasta) {
        String jpql = "SELECT f FROM FacturaVenta f WHERE f.fechaEmision BETWEEN :fechaDesde AND :fechaHasta";
        return em.createQuery(jpql, FacturaVenta.class)
                .setParameter("fechaDesde", desde)
                 .setParameter("fechaHasta", hasta)
                 .getResultList();
    }

    // =========================================================================
    // NIVEL 2: Condicionales Combinados, Operadores de Texto y Agregaciones
    // =========================================================================

    // 5. Facturas "EMITIDA", importeTotal > $10,000 y fechaAnulacion sea nula.
    public static List<FacturaVenta> obtenerFacturasValidasAltas(EntityManager em, double minImporte) {
        String jpql = "SELECT f FROM FacturaVenta f " +
                "WHERE f.estado = 'EMITIDA' " +
                "AND f.importeTotal > :minImporte " +
                "AND f.fechaAnulacion IS NULL";
        return em.createQuery(jpql, FacturaVenta.class)
                .setParameter("minImporte", minImporte)
                 .getResultList();
    }

    // 6. Clientes por denominación parcial (ignore case) o CUIT/CUIL comience con "20-".
    public static List<Cliente> buscarClientesPorNombreOCuit(EntityManager em, String textoNombre, String prefijoCuit) {
        String jpql = "SELECT c FROM Cliente c " +
                "WHERE LOWER(c.denominacion) LIKE LOWER(:texto) " +
                "OR c.cuitCuil LIKE :cuitPrefijo";
        return em.createQuery(jpql, Cliente.class)
                .setParameter("texto", "%" + textoNombre + "%")
                 .setParameter("cuitPrefijo", prefijoCuit + "%")
                 .getResultList();
    }

    // 7. Estados posibles de facturas de venta sin duplicados, ordenados ascendentemente.
    public static List<String> obtenerEstadosFacturasUnicos(EntityManager em) {
        String jpql = "SELECT DISTINCT f.estado FROM FacturaVenta f ORDER BY f.estado ASC";
        return em.createQuery(jpql, String.class).getResultList();
    }

    // 8. Cantidad de facturas emitidas, suma acumulada y promedio devuelto en un arreglo Object[].
    public static Object[] obtenerEstadisticasFacturasEmitidas(EntityManager em) {
        String jpql = "SELECT COUNT(f), SUM(f.importeTotal), AVG(f.importeTotal) " +
                "FROM FacturaVenta f WHERE f.estado = 'EMITIDA'";
        return em.createQuery(jpql, Object[].class).getSingleResult();
    }

    // 9. Puntos de venta cuyo número coincida con una lista enviada por parámetro.
    public static List<PuntoVenta> obtenerPuntosVentaPorNumeros(EntityManager em, List<Integer> numeros) {
        String jpql = "SELECT pv FROM PuntoVenta pv WHERE pv.numero IN :listaNumeros";
        return em.createQuery(jpql, PuntoVenta.class)
                .setParameter("listaNumeros", numeros)
                 .getResultList();
    }

    // =========================================================================
    // NIVEL 3: Navegación de Entidades, JOINs y Subconsultas Simples
    // =========================================================================

    // 10. Facturas creadas por un usuario según su username (navegación implícita).
    public static List<FacturaVenta> obtenerFacturasPorUsuarioCarga(EntityManager em, String username) {
        String jpql = "SELECT f FROM FacturaVenta f WHERE f.usuarioCarga.usuario = :username";
        return em.createQuery(jpql, FacturaVenta.class)
                .setParameter("username", username)
                 .getResultList();
    }

    // 11. Detalles de factura correspondientes a un punto de venta determinado.
    public static List<FacturaVentaDetalle> obtenerDetallesPorNumeroPuntoVenta(EntityManager em, int numeroPV) {
        String jpql = "SELECT d FROM FacturaVentaDetalle d " +
                "INNER JOIN d.factura f " +
        "WHERE f.puntoVenta.numero = :numeroPV";
        return em.createQuery(jpql, FacturaVentaDetalle.class)
                .setParameter("numeroPV", numeroPV)
                 .getResultList();
    }

    // 12. Denominación de artículos con denominación de su marca (LEFT JOIN).
    public static List<Object[]> obtenerArticulosConMarca(EntityManager em) {
        String jpql = "SELECT a.denominacion, m.denominacion " +
                "FROM Articulo a LEFT JOIN a.marca m";
        return em.createQuery(jpql, Object[].class).getResultList();
    }

    // 13. Facturas que contengan al menos un detalle de un artículo de una marca específica.
    public static List<FacturaVenta> obtenerFacturasPorMarcaArticulo(EntityManager em, String marcaNombre) {
        String jpql = "SELECT DISTINCT f FROM FacturaVenta f " +
                "JOIN f.detalles d " +
        "JOIN d.listaPrecioArticulo lpa " +
                "JOIN lpa.articulo a " +
                "WHERE a.marca.denominacion = :marcaNombre";
        return em.createQuery(jpql, FacturaVenta.class)
                .setParameter("marcaNombre", marcaNombre)
                 .getResultList();
    }

    // 14. Facturas cuyo importeTotal sea mayor al promedio general.
    public static List<FacturaVenta> obtenerFacturasSuperioresAlPromedio(EntityManager em) {
        String jpql = "SELECT f FROM FacturaVenta f " +
                "WHERE f.importeTotal > (SELECT AVG(f2.importeTotal) FROM FacturaVenta f2)";
        return em.createQuery(jpql, FacturaVenta.class).getResultList();
    }

    // 15. Facturas asociadas a un cliente por su CUIT/CUIL.
    public static List<FacturaVenta> obtenerFacturasPorCuitCliente(EntityManager em, String cuit) {
        String jpql = "SELECT f FROM FacturaVenta f WHERE f.cliente.cuitCuil = :cuitCliente";
        return em.createQuery(jpql, FacturaVenta.class)
                .setParameter("cuitCliente", cuit)
                 .getResultList();
    }

    // =========================================================================
    // NIVEL 4: Agrupamiento (GROUP BY) y Filtros de Grupo (HAVING)
    // =========================================================================

    // 16. Descripción del punto de venta, cantidad de facturas y suma total.
    public static List<Object[]> obtenerResumenFacturacionPorPuntoVenta(EntityManager em) {
        String jpql = "SELECT pv.descripcion, COUNT(f), SUM(f.importeTotal) " +
                "FROM FacturaVenta f JOIN f.puntoVenta pv " +
        "GROUP BY pv.id, pv.descripcion";
        return em.createQuery(jpql, Object[].class).getResultList();
    }

    // 17. Nombres de usuarios de carga que hayan registrado más de N facturas.
    public static List<String> obtenerUsuariosConMasDeNFacturas(EntityManager em, long minFacturas) {
        String jpql = "SELECT u.usuario FROM FacturaVenta f " +
                "JOIN f.usuarioCarga u " +
        "GROUP BY u.id, u.usuario " +
        "HAVING COUNT(f) > :minFacturas";
        return em.createQuery(jpql, String.class)
                .setParameter("minFacturas", minFacturas)
                 .getResultList();
    }

    // 18. Denominación de marca, total unidades vendidas y subtotal acumulado.
    public static List<Object[]> obtenerVentasTotalesPorMarca(EntityManager em) {
        String jpql = "SELECT m.denominacion, SUM(d.cantidad), SUM(d.importeSubtotal) " +
                "FROM FacturaVentaDetalle d " +
                "JOIN d.listaPrecioArticulo lpa " +
                "JOIN lpa.articulo a " +
                "JOIN a.marca m " +
        "GROUP BY m.id, m.denominacion";
        return em.createQuery(jpql, Object[].class).getResultList();
    }

    // 19. Denominación de Condición de IVA y suma total facturada.
    public static List<Object[]> obtenerTotalFacturadoPorCondicionIva(EntityManager em) {
        String jpql = "SELECT c.denominacion, SUM(f.importeTotal) " +
                "FROM FacturaVenta f JOIN f.condicionIva c " +
        "GROUP BY c.id, c.denominacion";
        return em.createQuery(jpql, Object[].class).getResultList();
    }

    // =========================================================================
    // NIVEL 5: Subconsultas Correlacionadas, EXISTS, NOT EXISTS y CASE
    // =========================================================================

    // 20. Marcas con al menos un artículo facturado (EXISTS correlacionado).
    public static List<Marca> obtenerMarcasConArticulosFacturados(EntityManager em) {
        String jpql = "SELECT m FROM Marca m " +
                "WHERE EXISTS (" +
        "   SELECT d FROM FacturaVentaDetalle d " +
                "   WHERE d.listaPrecioArticulo.articulo.marca = m" +
                ")";
        return em.createQuery(jpql, Marca.class).getResultList();
    }

    // 21. Artículos que NUNCA han sido facturados (NOT EXISTS correlacionado).
    public static List<Articulo> obtenerArticulosNuncaFacturados(EntityManager em) {
        String jpql = "SELECT a FROM Articulo a " +
                "WHERE NOT EXISTS (" +
        "   SELECT d FROM FacturaVentaDetalle d " +
                "   WHERE d.listaPrecioArticulo.articulo = a" +
                ")";
        return em.createQuery(jpql, Articulo.class).getResultList();
    }

    // 22. Proyección condicional CASE WHEN para categorización de facturas.
    public static List<Object[]> obtenerFacturasCategorizadas(EntityManager em) {
        String jpql = "SELECT f.numero, f.importeTotal, " +
                "CASE " +
                "   WHEN f.importeTotal > 50000 THEN 'ALTO VALOR' " +
                "   WHEN f.importeTotal BETWEEN 10000 AND 50000 THEN 'MEDIO VALOR' " +
                "   ELSE 'BAJO VALOR' " +
                "END " +
                "FROM FacturaVenta f " +
                "ORDER BY f.importeTotal DESC";
        return em.createQuery(jpql, Object[].class).getResultList();
    }
}