package com.example.facturacion.services;

import com.example.facturacion.dto.FacturaReporteDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.entities.Cliente;
import com.example.facturacion.entities.CondicionIva;
import com.example.facturacion.entities.ListaPrecioArticulo;
import com.example.facturacion.entities.PuntoVenta;
import com.example.facturacion.entities.TipoMoneda;
import com.example.facturacion.entities.Usuario;
import com.example.facturacion.repositories.ClienteRepository;
import com.example.facturacion.repositories.CondicionIvaRepository;
import com.example.facturacion.repositories.FacturaVentaRepository;
import com.example.facturacion.repositories.ListaPrecioArticuloRepository;
import com.example.facturacion.repositories.PuntoVentaRepository;
import com.example.facturacion.repositories.TipoMonedaRepository;
import com.example.facturacion.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.facturacion.dto.FacturaRequestDTO;
import com.example.facturacion.dto.FacturaDetalleRequestDTO;

import com.example.facturacion.entities.FacturaVenta;
import com.example.facturacion.entities.FacturaVentaDetalle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FacturaService {

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
            + " f.estado "
            + ") "
            + "FROM FacturaVenta f "
            + "LEFT JOIN f.cliente c "
            + "JOIN f.condicionIva ci "
            + "JOIN f.puntoVenta pv "
            + "LEFT JOIN f.detalles d ";

    private static final String GROUP_BY
            = "GROUP BY f.id, f.numero, f.fechaEmision, c.denominacion, "
            + "ci.denominacion, pv.descripcion, f.importeTotal, f.estado";

    @Autowired
    private FacturaVentaRepository facturaVentaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CondicionIvaRepository condicionIvaRepository;

    @Autowired
    private PuntoVentaRepository puntoVentaRepository;

    @Autowired
    private TipoMonedaRepository tipoMonedaRepository;

    @Autowired
    private ListaPrecioArticuloRepository listaPrecioArticuloRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> reporteFacturas() {
        return em.createQuery(SELECT_BASE + GROUP_BY, FacturaReporteDTO.class).getResultList();
    }

    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta) {
        return buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta, "fechaEmision", "desc");
    }

    public List<FacturaReporteDTO> buscarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta, String sortBy, String sortDir) {
        return buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta, -1, -1, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta, int page, int size) {
        return buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta, page, size,
                "fechaEmision", "desc");
    }

    public List<FacturaReporteDTO> buscarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta, int page, int size, String sortBy, String sortDir) {
        StringBuilder jpql = new StringBuilder(SELECT_BASE).append("WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

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
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        if (sortDir != null && !"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
            throw new IllegalArgumentException("Dirección de ordenamiento no válida");
        }

        jpql.append(GROUP_BY).append(" ORDER BY ").append(orderExpression).append(" ")
                .append(direction).append(", f.id DESC");

        TypedQuery<FacturaReporteDTO> query = em.createQuery(jpql.toString(), FacturaReporteDTO.class);
        params.forEach(query::setParameter);

        if (page >= 0 && size > 0) {
            query.setFirstResult(page * size);
            query.setMaxResults(size);
        }

        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public long contarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(f) FROM FacturaVenta f WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

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

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    private static final double IVA_PORCENTAJE = 21.0;

    public synchronized long obtenerSiguienteNumero() {
        long siguiente = Math.max(1L, facturaVentaRepository.obtenerNumeroMaximo() + 1L);
        while (facturaVentaRepository.existsByNumero(siguiente)) {
            siguiente++;
        }
        return siguiente;
    }

    @Transactional
    public synchronized FacturaVenta crearFactura(FacturaRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Los datos de la factura son obligatorios");
        }

        if (request.getPuntoVentaId() == null
                || request.getCondicionIvaId() == null
                || request.getTipoMonedaId() == null) {
            throw new IllegalArgumentException(
                    "Punto de venta, condición de IVA y moneda son obligatorios");
        }

        if (request.getDetalles() == null
                || request.getDetalles().isEmpty()) {
            throw new IllegalArgumentException(
                    "La factura debe tener al menos un detalle");
        }

        // 1. Recuperar las entidades relacionadas.
        PuntoVenta puntoVenta = puntoVentaRepository
                .findById(request.getPuntoVentaId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el punto de venta indicado"));

        CondicionIva condicionIva = condicionIvaRepository
                .findById(request.getCondicionIvaId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la condición de IVA indicada"));

        TipoMoneda tipoMoneda = tipoMonedaRepository
                .findById(request.getTipoMonedaId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el tipo de moneda indicado"));

        Cliente cliente = null;

        if (request.getClienteId() != null) {
            cliente = clienteRepository.findById(request.getClienteId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el cliente indicado"));
        }

        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));

        // 2. Construir la cabecera de la factura.
        FacturaVenta factura = new FacturaVenta();
        LocalDate hoy = LocalDate.now();
        long siguienteNumero = obtenerSiguienteNumero();

        factura.setNumero(siguienteNumero);
        factura.setFechaEmision(hoy);
        factura.setCliente(cliente);
        factura.setCondicionIva(condicionIva);
        factura.setTipoMoneda(tipoMoneda);
        factura.setPuntoVenta(puntoVenta);
        factura.setEstado(
                request.getEstado() == null
                || request.getEstado().isBlank()
                ? "PENDIENTE"
                : request.getEstado());
        factura.setObservaciones(request.getObservaciones());

        factura.setFechaAlta(hoy);
        factura.setFechaModificacion(hoy);
        factura.setUsuarioCarga(usuario);
        factura.setUsuarioModificacion(usuario);

        // 3. Construir los detalles y calcular sus importes.
        double total = 0.0;

        for (FacturaDetalleRequestDTO detalleRequest : request.getDetalles()) {

            if (detalleRequest.getArticuloId() == null) {
                throw new IllegalArgumentException(
                        "Cada detalle debe indicar un artículo");
            }

            if (!Double.isFinite(detalleRequest.getCantidad())
                    || detalleRequest.getCantidad() < 1
                    || detalleRequest.getCantidad() % 1 != 0) {
                throw new IllegalArgumentException(
                        "La cantidad debe ser un número entero mayor que cero");
            }

            double bonificacion
                    = detalleRequest.getPorcentajeBonificacion();

            if (bonificacion < 0 || bonificacion > 100) {
                throw new IllegalArgumentException(
                        "La bonificación debe estar entre 0 y 100");
            }

            ListaPrecioArticulo precioArticulo
                    = listaPrecioArticuloRepository
                            .findFirstByArticulo_Id(
                                    detalleRequest.getArticuloId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No existe una lista de precios para el artículo "
                                    + detalleRequest.getArticuloId()));

            double precioUnitario = precioArticulo.getPrecioVenta();

            double neto = redondear(
                    detalleRequest.getCantidad()
                    * precioUnitario
                    * (1 - bonificacion / 100));

            double iva = redondear(neto * IVA_PORCENTAJE / 100);

            double subtotal = redondear(neto + iva);

            FacturaVentaDetalle detalle = new FacturaVentaDetalle();
            detalle.setListaPrecioArticulo(precioArticulo);

            String descripcion = detalleRequest.getDescripcion();

            if (descripcion == null || descripcion.isBlank()) {
                descripcion = precioArticulo.getArticulo().getDenominacion();
            }

            detalle.setDescripcion(descripcion);
            detalle.setCantidad(detalleRequest.getCantidad());
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setPorcentajeBonificacion(bonificacion);
            detalle.setImporteNeto(neto);
            detalle.setImporteIva(iva);
            detalle.setImporteSubtotal(subtotal);

            factura.addDetalle(detalle);
            total += subtotal;
        }

        // 4. La factura emitida queda cobrada por su total.
        total = redondear(total);
        factura.setImporteTotal(total);
        factura.setImporteCobrado(total);
        factura.setImporteSaldo(0.0);

        // 5. Guardar la factura junto con sus detalles.
        return facturaVentaRepository.save(factura);
    }

    @Transactional
    public FacturaVenta actualizarFactura(Long id, FacturaRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException("Los datos de la factura son obligatorios");
        }

        // 1. Buscar la factura existente.
        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));

        // 2. Validar los datos obligatorios.
        if (request.getPuntoVentaId() == null
                || request.getCondicionIvaId() == null
                || request.getTipoMonedaId() == null) {
            throw new IllegalArgumentException(
                    "Punto de venta, condición de IVA y moneda son obligatorios");
        }

        if (request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new IllegalArgumentException(
                    "La factura debe tener al menos un detalle");
        }

        // 3. Buscar las entidades relacionadas.
        PuntoVenta puntoVenta = puntoVentaRepository.findById(request.getPuntoVentaId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el punto de venta indicado"));

        CondicionIva condicionIva = condicionIvaRepository
                .findById(request.getCondicionIvaId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la condición de IVA indicada"));

        TipoMoneda tipoMoneda = tipoMonedaRepository
                .findById(request.getTipoMonedaId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el tipo de moneda indicado"));

        Cliente cliente = null;

        if (request.getClienteId() != null) {
            cliente = clienteRepository.findById(request.getClienteId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el cliente indicado"));
        }

        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));

        // 4. Actualizar la cabecera sin crear otra factura.
        LocalDate hoy = LocalDate.now();

        factura.setCliente(cliente);
        factura.setCondicionIva(condicionIva);
        factura.setTipoMoneda(tipoMoneda);
        factura.setPuntoVenta(puntoVenta);

        if (request.getEstado() != null && !request.getEstado().isBlank()) {
            boolean estabaAnulada = "ANULADA".equalsIgnoreCase(factura.getEstado());
            factura.setEstado(request.getEstado());
            if (!"ANULADA".equalsIgnoreCase(request.getEstado())
                    && (estabaAnulada
                    || factura.getFechaAnulacion() != null
                    || factura.getFechaBaja() != null)) {
                limpiarDatosBaja(factura);
            }
        }

        factura.setObservaciones(request.getObservaciones());
        factura.setFechaModificacion(hoy);
        factura.setUsuarioModificacion(usuario);

        // 5. Reemplazar los detalles anteriores.
        factura.getDetalles().clear();

        double total = 0.0;

        for (FacturaDetalleRequestDTO detalleRequest : request.getDetalles()) {

            if (detalleRequest.getArticuloId() == null) {
                throw new IllegalArgumentException(
                        "Cada detalle debe indicar un artículo");
            }

            if (!Double.isFinite(detalleRequest.getCantidad())
                    || detalleRequest.getCantidad() < 1
                    || detalleRequest.getCantidad() % 1 != 0) {
                throw new IllegalArgumentException(
                        "La cantidad debe ser un número entero mayor que cero");
            }

            double bonificacion = detalleRequest.getPorcentajeBonificacion();

            if (bonificacion < 0 || bonificacion > 100) {
                throw new IllegalArgumentException(
                        "La bonificación debe estar entre 0 y 100");
            }

            ListaPrecioArticulo precioArticulo = listaPrecioArticuloRepository
                    .findFirstByArticulo_Id(detalleRequest.getArticuloId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe una lista de precios para el artículo "
                            + detalleRequest.getArticuloId()));

            double precioUnitario = precioArticulo.getPrecioVenta();

            double neto = redondear(
                    detalleRequest.getCantidad()
                    * precioUnitario
                    * (1 - bonificacion / 100));

            double iva = redondear(neto * IVA_PORCENTAJE / 100);
            double subtotal = redondear(neto + iva);

            FacturaVentaDetalle detalle = new FacturaVentaDetalle();
            detalle.setListaPrecioArticulo(precioArticulo);

            String descripcion = detalleRequest.getDescripcion();

            if (descripcion == null || descripcion.isBlank()) {
                descripcion = precioArticulo.getArticulo().getDenominacion();
            }

            detalle.setDescripcion(descripcion);
            detalle.setCantidad(detalleRequest.getCantidad());
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setPorcentajeBonificacion(bonificacion);
            detalle.setImporteNeto(neto);
            detalle.setImporteIva(iva);
            detalle.setImporteSubtotal(subtotal);

            factura.addDetalle(detalle);
            total += subtotal;
        }

        // 6. Recalcular total; la factura queda cobrada por su importe total.
        total = redondear(total);
        factura.setImporteTotal(total);
        factura.setImporteCobrado(total);
        factura.setImporteSaldo(0.0);

        // 7. Guardar los cambios.
        return facturaVentaRepository.save(factura);
    }

    @Transactional
    public void anularFactura(Long id) {

        // 1. Buscar la factura.
        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));

        // 2. Evitar anularla más de una vez.
        if ("ANULADA".equalsIgnoreCase(factura.getEstado())
                || factura.getFechaAnulacion() != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "La factura ya está anulada");
        }

        // 3. Registrar quién hizo la operación.
        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));

        // 4. Realizar el borrado lógico.
        LocalDate hoy = LocalDate.now();

        factura.setEstado("ANULADA");
        factura.setFechaAnulacion(hoy);
        factura.setFechaBaja(hoy);
        factura.setFechaModificacion(hoy);
        factura.setUsuarioModificacion(usuario);
        factura.setUsuarioBaja(usuario);

        // 5. Guardar los cambios sin eliminar el registro.
        facturaVentaRepository.save(factura);
    }

    @Transactional
    public void reactivarFactura(Long id) {
        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));

        if (!"ANULADA".equalsIgnoreCase(factura.getEstado())
                && factura.getFechaAnulacion() == null
                && factura.getFechaBaja() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "La factura no está anulada");
        }

        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));

        factura.setEstado("EMITIDA");
        limpiarDatosBaja(factura);
        factura.setFechaModificacion(LocalDate.now());
        factura.setUsuarioModificacion(usuario);
        facturaVentaRepository.save(factura);
    }

    private void limpiarDatosBaja(FacturaVenta factura) {
        factura.setFechaAnulacion(null);
        factura.setFechaBaja(null);
        factura.setUsuarioBaja(null);
    }

    private double redondear(double importe) {
        return BigDecimal.valueOf(importe)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
