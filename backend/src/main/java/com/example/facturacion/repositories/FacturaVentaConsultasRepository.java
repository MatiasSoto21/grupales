package com.example.facturacion.repositories;

import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.dto.FacturaFiltrosDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class FacturaVentaConsultasRepository {
    @PersistenceContext
    private EntityManager em;

    private static final String SELECT_BASE
            = "SELECT new com.example.facturacion.dto.FacturaReporteDTO("
            + " f.id, "
            + " f.numero, "
            + " f.fechaEmision, "
            + " COALESCE(c.denominacion, 'Consumidor Final'), "
            + " ci.denominacion, "
            + " pv.descripcion, "
            + " f.importeTotal, "
            + " COUNT(d), "
            + " f.estado, "
            + " CASE WHEN f.cae IS NOT NULL AND f.cae <> '' THEN true ELSE false END, "
            + " COALESCE(f.comprobanteAfipDisponible, false), "
            + " CASE WHEN (f.cae IS NOT NULL AND f.cae <> '') OR f.resultadoAfip = 'A' "
            + " OR f.comprobanteAfipDisponible = true OR f.reemplazadoPorFiscal = true OR (f.comprobanteFiscalDerivado IS NOT NULL AND (f.tipoComprobanteAfip IS NULL OR f.tipoComprobanteAfip <> 0)) OR f.estado IN ('AUTORIZADA', 'ERROR_AFIP', 'ANULADA') THEN false ELSE true END, "
            + " COALESCE(f.receptorPrueba, false), f.motivoRechazo, CASE WHEN f.tipoComprobanteAfip = 0 THEN true ELSE false END, f.origenComprobante.id, f.comprobanteFiscalDerivado.id, pv.id, COALESCE(f.puntoVentaNumeroAfip, pv.numero), f.tipoComprobanteAfip "
            + ") "
            + "FROM FacturaVenta f "
            + "LEFT JOIN f.cliente c "
            + "JOIN f.condicionIva ci "
            + "JOIN f.puntoVenta pv "
            + "LEFT JOIN f.detalles d ";

    private static final String GROUP_BY
            = "GROUP BY f.id, f.numero, f.fechaEmision, c.denominacion, "
            + "ci.denominacion, pv.descripcion, f.importeTotal, f.estado, f.cae, "
            + "f.comprobanteAfipDisponible, f.resultadoAfip, f.receptorPrueba, f.motivoRechazo, f.tipoComprobanteAfip, f.origenComprobante.id, f.comprobanteFiscalDerivado.id, pv.id, pv.numero, f.puntoVentaNumeroAfip, f.reemplazadoPorFiscal";

    public List<FacturaReporteDTO> reporteFacturas() {
        return em.createQuery(SELECT_BASE + GROUP_BY, FacturaReporteDTO.class).getResultList();
    }

    public List<FacturaReporteDTO> buscarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta, int page, int size, String sortBy, String sortDir) {
        return buscar(FacturaFiltrosDTO.desde(fechaDesde, fechaHasta, estado, montoMinimo, clienteId, sinCliente,
                numero, numeroDesde, numeroHasta, page, size, sortBy, sortDir), true);
    }

    public List<FacturaReporteDTO> buscar(FacturaFiltrosDTO filtros, boolean paginar) {
        LocalDate fechaDesde = filtros.getFechaDesde(), fechaHasta = filtros.getFechaHasta();
        String estado = filtros.getEstado(), sortBy = filtros.getSortBy(), sortDir = filtros.getSortDir();
        Double montoMinimo = filtros.getMontoMinimo(); Long clienteId = filtros.getClienteId(), numero = filtros.getNumero();
        boolean sinCliente = filtros.isSinCliente(); Long numeroDesde = filtros.getNumeroDesde(), numeroHasta = filtros.getNumeroHasta();
        int page = filtros.getPage(), size = filtros.getSize();
        StringBuilder jpql = new StringBuilder(SELECT_BASE).append("WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        aplicarFiltros(jpql, params, fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta);
        aplicarVista(jpql, params, filtros);
        String orderExpression;
        if ("numero".equalsIgnoreCase(sortBy)) {
            orderExpression = "f.numero";
        } else if ("total".equalsIgnoreCase(sortBy)) {
            orderExpression = "f.importeTotal";
        } else if ("fechaEmision".equalsIgnoreCase(sortBy)) {
            orderExpression = "f.fechaEmision";
        } else {
            throw new IllegalArgumentException("Campo de ordenamiento no válido");
        }
        if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
            throw new IllegalArgumentException("Dirección de ordenamiento no válida");
        }
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";

        jpql.append(GROUP_BY).append(" ORDER BY ").append(orderExpression).append(" ")
                .append(direction).append(", f.id DESC");

        TypedQuery<FacturaReporteDTO> query = em.createQuery(jpql.toString(), FacturaReporteDTO.class);
        params.forEach(query::setParameter);

        if (paginar && page >= 0 && size > 0) {
            query.setFirstResult(page * size);
            query.setMaxResults(size);
        }

        return query.getResultList();
    }

    public long contarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta) {
        return contar(FacturaFiltrosDTO.desde(fechaDesde, fechaHasta, estado, montoMinimo, clienteId, sinCliente,
                numero, numeroDesde, numeroHasta, 0, 10, "fechaEmision", "desc"));
    }

    public long contar(FacturaFiltrosDTO filtros) {
        LocalDate fechaDesde = filtros.getFechaDesde(), fechaHasta = filtros.getFechaHasta();
        String estado = filtros.getEstado(); Double montoMinimo = filtros.getMontoMinimo();
        Long clienteId = filtros.getClienteId(), numero = filtros.getNumero(), numeroDesde = filtros.getNumeroDesde(), numeroHasta = filtros.getNumeroHasta();
        boolean sinCliente = filtros.isSinCliente();
        StringBuilder jpql = new StringBuilder("SELECT COUNT(f) FROM FacturaVenta f WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        aplicarFiltros(jpql, params, fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta);
        aplicarVista(jpql, params, filtros);

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    private void aplicarFiltros(StringBuilder jpql, Map<String, Object> params,
            LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta) {
        if (fechaDesde != null) {
            jpql.append("AND f.fechaEmision >= :fechaDesde ");
            params.put("fechaDesde", fechaDesde);
        }
        if (fechaHasta != null) {
            jpql.append("AND f.fechaEmision <= :fechaHasta ");
            params.put("fechaHasta", fechaHasta);
        }
        if (estado != null && !estado.trim().isEmpty()) {
            jpql.append("AND f.estado = :estado ");
            params.put("estado", estado);
        }
        if (montoMinimo != null) {
            jpql.append("AND f.importeTotal >= :montoMinimo ");
            params.put("montoMinimo", montoMinimo);
        }
        if (clienteId != null) {
            jpql.append("AND f.cliente.id = :clienteId ");
            params.put("clienteId", clienteId);
        }
        if (sinCliente) {
            jpql.append("AND f.cliente IS NULL ");
        }
        if (numero != null) {
            jpql.append("AND f.numero = :numero ");
            params.put("numero", numero);
        }
        if (numeroDesde != null) {
            jpql.append("AND f.numero >= :numeroDesde ");
            params.put("numeroDesde", numeroDesde);
        }
        if (numeroHasta != null) {
            jpql.append("AND f.numero <= :numeroHasta ");
            params.put("numeroHasta", numeroHasta);
        }
    }
    public java.util.Set<Long> buscarIdsConNumeroEnOtraSucursal(List<Long> ids, boolean incluirVinculados) {
        if (ids.isEmpty()) return java.util.Set.of();
        String jpql = "SELECT f.id FROM FacturaVenta f WHERE f.id IN :ids AND f.numero IS NOT NULL "
                + "AND EXISTS (SELECT otra.id FROM FacturaVenta otra WHERE otra.numero = f.numero "
                + "AND otra.puntoVenta.id <> f.puntoVenta.id "
                + "AND ((f.tipoComprobanteAfip = 0 AND otra.tipoComprobanteAfip = 0) "
                + "OR ((f.tipoComprobanteAfip IS NULL OR f.tipoComprobanteAfip <> 0) "
                + "AND (otra.tipoComprobanteAfip IS NULL OR otra.tipoComprobanteAfip <> 0))) "
                + (incluirVinculados ? "" : "AND " + vistaActual("otra") + " ") + ")";
        return new java.util.HashSet<>(em.createQuery(jpql, Long.class).setParameter("ids", ids).getResultList());
    }

    private String vistaActual(String alias) {
        return "(" + alias + ".comprobanteFiscalDerivado IS NULL OR (" + alias
                + ".tipoComprobanteAfip = 0 AND " + alias + ".reemplazadoPorFiscal = false))";
    }

    // Only derived visibility metadata changes; historical fiscal snapshots are never rewritten.
    public int marcarXReemplazadosPorAutorizacionesExistentes() {
        return em.createNativeQuery("""
                WITH RECURSIVE origenes(id) AS (
                    SELECT origen_comprobante_id FROM ventas.factura_venta
                    WHERE resultado_afip = 'A' AND cae IS NOT NULL AND cae <> '' AND origen_comprobante_id IS NOT NULL
                    UNION
                    SELECT f.origen_comprobante_id FROM ventas.factura_venta f JOIN origenes o ON f.id = o.id
                    WHERE f.origen_comprobante_id IS NOT NULL
                )
                UPDATE ventas.factura_venta x SET reemplazado_por_fiscal = true, version = version + 1
                WHERE x.tipo_comprobante_afip = 0 AND x.reemplazado_por_fiscal = false
                AND x.id IN (SELECT id FROM origenes)
                """).executeUpdate();
    }

    private void aplicarVista(StringBuilder jpql, Map<String, Object> params, FacturaFiltrosDTO filtros) {
        if (!filtros.isIncluirVinculados()) jpql.append("AND ").append(vistaActual("f")).append(" ");
        if (filtros.getPuntoVentaId() != null) {
            jpql.append("AND f.puntoVenta.id = :puntoVentaId "); params.put("puntoVentaId", filtros.getPuntoVentaId());
        }
        if ("X".equals(filtros.getClaseDocumento())) jpql.append("AND f.tipoComprobanteAfip = 0 ");
        if ("FISCAL".equals(filtros.getClaseDocumento())) jpql.append("AND (f.tipoComprobanteAfip IS NULL OR f.tipoComprobanteAfip <> 0) ");
    }
}
