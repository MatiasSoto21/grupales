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
            String estado, Double montoMinimo) {
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
        jpql.append(GROUP_BY);

        TypedQuery<FacturaReporteDTO> query = em.createQuery(jpql.toString(), FacturaReporteDTO.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    private static final double IVA_PORCENTAJE = 21.0;

    @Transactional
    public FacturaVenta crearFactura(FacturaRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Los datos de la factura son obligatorios");
        }

        if (request.getFechaEmision() == null) {
            throw new IllegalArgumentException(
                    "La fecha de emisión es obligatoria");
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

        factura.setNumero(request.getNumero());
        factura.setFechaEmision(request.getFechaEmision());
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

            if (detalleRequest.getCantidad() <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad debe ser mayor que cero");
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

        // 4. Calcular el total y el saldo.
        total = redondear(total);

        double cobrado = request.getImporteCobrado();

        if (cobrado < 0 || cobrado > total) {
            throw new IllegalArgumentException(
                    "El importe cobrado debe estar entre cero y el total");
        }

        factura.setImporteTotal(total);
        factura.setImporteCobrado(redondear(cobrado));
        factura.setImporteSaldo(redondear(total - cobrado));

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
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la factura con ID " + id));

        // 2. Validar los datos obligatorios.
        if (request.getFechaEmision() == null) {
            throw new IllegalArgumentException("La fecha de emisión es obligatoria");
        }

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

        if (request.getNumero() != null) {
            factura.setNumero(request.getNumero());
        }

        factura.setFechaEmision(request.getFechaEmision());
        factura.setCliente(cliente);
        factura.setCondicionIva(condicionIva);
        factura.setTipoMoneda(tipoMoneda);
        factura.setPuntoVenta(puntoVenta);

        if (request.getEstado() != null && !request.getEstado().isBlank()) {
            factura.setEstado(request.getEstado());
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

            if (detalleRequest.getCantidad() <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad debe ser mayor que cero");
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

        // 6. Recalcular total, cobrado y saldo.
        total = redondear(total);
        double cobrado = request.getImporteCobrado();

        if (cobrado < 0 || cobrado > total) {
            throw new IllegalArgumentException(
                    "El importe cobrado debe estar entre cero y el total");
        }

        factura.setImporteTotal(total);
        factura.setImporteCobrado(redondear(cobrado));
        factura.setImporteSaldo(redondear(total - cobrado));

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

    private double redondear(double importe) {
        return BigDecimal.valueOf(importe)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
