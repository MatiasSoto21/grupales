package com.example.facturacion.services;

import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.dto.FacturaComprobanteDTO;
import com.example.facturacion.config.EmisorComprobanteProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.entities.Cliente;
import com.example.facturacion.entities.CondicionIva;
import com.example.facturacion.entities.ListaPrecioArticulo;
import com.example.facturacion.entities.PuntoVenta;
import com.example.facturacion.entities.TipoMoneda;
import com.example.facturacion.entities.Usuario;
import com.example.facturacion.repositories.ClienteRepository;
import com.example.facturacion.repositories.BloqueoTransaccionalRepository;
import com.example.facturacion.repositories.CondicionIvaRepository;
import com.example.facturacion.repositories.FacturaVentaRepository;
import com.example.facturacion.repositories.ListaPrecioArticuloRepository;
import com.example.facturacion.repositories.PuntoVentaRepository;
import com.example.facturacion.repositories.TipoMonedaRepository;
import com.example.facturacion.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.facturacion.dto.FacturaRequestDTO;
import com.example.facturacion.dto.FacturaDetalleRequestDTO;

import com.example.facturacion.entities.FacturaVenta;
import com.example.facturacion.entities.FacturaVentaDetalle;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class FacturaService {

    @org.springframework.beans.factory.annotation.Value("${facturacion.arca.clave-path:classpath:privada_test.key}")
    private String keyPath;
    @org.springframework.beans.factory.annotation.Value("${facturacion.arca.certificado-path:classpath:certificado_test.crt}")
    private String certPath;

    @Autowired
    private FacturaVentaRepository facturaVentaRepository;

    @Autowired
    private com.example.facturacion.repositories.FacturaVentaConsultasRepository consultasFactura;

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

    @Autowired
    private AfipWsaaService afipWsaaService;

    @Autowired
    private AfipWsfeService afipWsfeService;

    @Autowired
    private EmisorComprobanteProperties emisorComprobanteProperties;

    @Value("${facturacion.arca.ambiente:HOMOLOGACION}")
    private String ambienteAfip;

    @Autowired
    private RequestValidation requestValidation;

    @Autowired
    private ReceptorArcaPolicy receptorArcaPolicy;

    @Autowired
    private FechaFacturacionPolicy fechas;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FacturaService.class);

    @Autowired
    private BloqueoTransaccionalRepository bloqueos;

    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> reporteFacturas() {
        return consultasFactura.reporteFacturas();
    }

    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta) {
        return buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta, "fechaEmision", "desc");
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta, int page, int size, String sortBy, String sortDir) {
        validarFiltros(fechaDesde, fechaHasta, montoMinimo, clienteId, sinCliente, numero, numeroDesde, numeroHasta);
        validarEstadoFiltro(estado);
        return consultasFactura.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta, page, size, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public long contarFacturasFiltradas(LocalDate fechaDesde, LocalDate fechaHasta,
            String estado, Double montoMinimo, Long clienteId, boolean sinCliente, Long numero,
            Long numeroDesde, Long numeroHasta) {
        validarFiltros(fechaDesde, fechaHasta, montoMinimo, clienteId, sinCliente, numero, numeroDesde, numeroHasta);
        validarEstadoFiltro(estado);
        return consultasFactura.contarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, sinCliente, numero, numeroDesde, numeroHasta);
    }

    @Transactional(readOnly = true)
    public com.example.facturacion.dto.PageResponseDTO<FacturaReporteDTO> listar(com.example.facturacion.dto.FacturaFiltrosDTO filtros) {
        validarConsulta(filtros);
        var content = consultasFactura.buscar(filtros, true);
        var repetidos = consultasFactura.buscarIdsConNumeroEnOtraSucursal(
                content.stream().map(FacturaReporteDTO::getId).toList(), filtros.isIncluirVinculados());
        content.forEach(factura -> {
            completarOrigenInterno(factura);
            factura.setNumeroRepetidoOtraSucursal(repetidos.contains(factura.getId()));
        });
        long total = consultasFactura.contar(filtros);
        int pages = Math.max(1, (int) Math.ceil((double) total / filtros.getSize()));
        return new com.example.facturacion.dto.PageResponseDTO<>(content, total, pages, filtros.getPage(), filtros.getSize());
    }

    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarParaReporte(com.example.facturacion.dto.FacturaFiltrosDTO filtros) {
        validarConsulta(filtros);
        return consultasFactura.buscar(filtros, false);
    }

    private void validarConsulta(com.example.facturacion.dto.FacturaFiltrosDTO f) {
        validarFiltros(f.getFechaDesde(), f.getFechaHasta(), f.getMontoMinimo(), f.getClienteId(), f.isSinCliente(), f.getNumero(), f.getNumeroDesde(), f.getNumeroHasta());
        validarEstadoFiltro(f.getEstado());
        if (f.getPage() < 0 || f.getSize() < 1 || f.getSize() > 100 || f.getPage() > Integer.MAX_VALUE / f.getSize())
            throw new IllegalArgumentException("La pagina debe ser >= 0 y el tamano debe estar entre 1 y 100");
        if (f.getSortBy() == null || !java.util.Set.of("numero", "fechaEmision", "total").contains(f.getSortBy())
                || f.getSortDir() == null || !java.util.Set.of("asc", "desc").contains(f.getSortDir()))
            throw new IllegalArgumentException("Ordenamiento no valido");
        if (f.getPuntoVentaId() != null && f.getPuntoVentaId() < 1) throw new IllegalArgumentException("Punto de venta no valido");
        if (f.getClaseDocumento() != null && !f.getClaseDocumento().isBlank() && !java.util.Set.of("X", "FISCAL").contains(f.getClaseDocumento()))
            throw new IllegalArgumentException("Clase de comprobante no valida");
    }

    @Transactional(readOnly = true)
    public FacturaRequestDTO obtenerFormulario(Long id) {
        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));

        List<FacturaDetalleRequestDTO> detalles
                = factura.getDetalles().stream()
                        .map(d -> new FacturaDetalleRequestDTO(
                                d.getListaPrecioArticulo()
                                        .getArticulo().getId(),
                                d.getListaPrecioArticulo().getId(),
                                d.getDescripcion(),
                                d.getCantidad(),
                                d.getPorcentajeBonificacion()))
                        .toList();

        FacturaRequestDTO dto = new FacturaRequestDTO(
                factura.getNumero(),
                factura.getFechaEmision(),
                factura.getCliente() != null
                ? factura.getCliente().getId()
                : null,
                factura.getCondicionIva().getId(),
                factura.getTipoMoneda().getId(),
                factura.getPuntoVenta().getId(),
                factura.getEstado(),
                factura.getObservaciones(),
                detalles);
        dto.setComprobanteInterno(factura.isComprobanteInterno());
        dto.setEditable(factura.isEditable());
        dto.setVersion(factura.getVersion());
        dto.setFechaOperacion(factura.getFechaOperacion());
        dto.setFechaSolicitudAfip(factura.getFechaSolicitudAfip());
        dto.setPuedeEmitirFiscal(factura.isPuedeEmitirFiscal());
        dto.setOrigenComprobanteId(factura.getOrigenComprobante() == null ? null : factura.getOrigenComprobante().getId());
        dto.setComprobanteFiscalDerivadoId(factura.getComprobanteFiscalDerivado() == null ? null : factura.getComprobanteFiscalDerivado().getId());

        dto.setReferenciaComprobante(resumen(factura).getReferenciaCompleta());
        if (factura.getComprobanteFiscalDerivado() != null)
            dto.setComprobanteFiscalReferencia(resumen(factura.getComprobanteFiscalDerivado()).getReferenciaCompleta());
        return dto;
    }

    @Transactional
    public FacturaReporteDTO crearResumen(FacturaRequestDTO request) { return resumen(crearFactura(request)); }
    @Transactional
    public FacturaReporteDTO actualizarResumen(Long id, FacturaRequestDTO request) { return resumen(actualizarFactura(id, request)); }
    @Transactional
    public FacturaReporteDTO emitirFiscalResumen(Long id, FacturaRequestDTO request) { return resumen(emitirFiscalDesde(id, request)); }

    private FacturaReporteDTO resumen(FacturaVenta factura) {
        FacturaReporteDTO reporte = new FacturaReporteDTO(
                factura.getId(),
                factura.getNumero(),
                factura.getFechaEmision(),
                factura.getCliente() != null
                ? factura.getCliente().getDenominacion()
                : "Consumidor Final",
                factura.getCondicionIva().getDenominacion(),
                factura.getPuntoVenta().getDescripcion(),
                factura.getImporteTotal(),
                factura.getDetalles().size(),
                factura.getEstado(),
                factura.getCae() != null && !factura.getCae().isBlank(),
                factura.isComprobanteAfipDisponible(),
                factura.isEditable(),
                factura.isReceptorPrueba(),
                factura.getMotivoRechazo(), factura.isComprobanteInterno()
        );

        reporte.setOrigenComprobanteId(factura.getOrigenComprobante() == null ? null : factura.getOrigenComprobante().getId());
        reporte.setComprobanteFiscalDerivadoId(factura.getComprobanteFiscalDerivado() == null ? null : factura.getComprobanteFiscalDerivado().getId());
        reporte.setPuntoVentaId(factura.getPuntoVenta().getId());
        reporte.setPuntoVentaNumero(factura.getPuntoVentaNumeroAfip() == null ? factura.getPuntoVenta().getNumero() : factura.getPuntoVentaNumeroAfip());
        reporte.setTipoComprobanteAfip(factura.getTipoComprobanteAfip());
        completarOrigenInterno(reporte);
        return reporte;
    }

    private void completarOrigenInterno(FacturaReporteDTO reporte) {
        Long originId = reporte.getOrigenComprobanteId();
        var visited = new java.util.HashSet<Long>();
        while (originId != null && visited.add(originId)) {
            var origin = facturaVentaRepository.findById(originId).orElse(null);
            if (origin == null) break;
            if (origin.isComprobanteInterno()) {
                reporte.setOrigenInternoId(origin.getId()); reporte.setOrigenInternoEditable(origin.isEditable());
                reporte.setOrigenInternoReferencia("X-" + String.format(java.util.Locale.ROOT, "%05d-%08d", origin.getPuntoVenta().getNumero(), origin.getNumero())
                        + " (" + origin.getPuntoVenta().getDescripcion() + ")");
                break;
            }
            originId = origin.getOrigenComprobante() == null ? null : origin.getOrigenComprobante().getId();
        }
    }

    static ReceptorAfip obtenerReceptorAfip(Cliente cliente) {
        if (cliente == null) {
            return new ReceptorAfip(99, 0L);
        }

        String cuitCuil = cliente.getCuitCuil();
        String numeroDocumento = cuitCuil == null ? "" : cuitCuil.replaceAll("\\D", "");
        if (numeroDocumento.length() != 11) {
            throw new IllegalArgumentException(
                    "El CUIT/CUIL del cliente debe contener 11 dígitos para emitir la factura");
        }

        return new ReceptorAfip(80, Long.parseLong(numeroDocumento));
    }

    record ReceptorAfip(int tipoDocumento, long numeroDocumento) {
    }

    static CondicionIva aplicarCondicionIvaCliente(
            Cliente cliente,
            CondicionIva condicionIvaSeleccionada) {
        if (condicionIvaSeleccionada == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La condición de IVA es obligatoria");
        }
        if (cliente == null) {
            return condicionIvaSeleccionada;
        }
        if (cliente.getCondicionIva() == null) {
            cliente.setCondicionIva(condicionIvaSeleccionada);
            return condicionIvaSeleccionada;
        }
        if (!cliente.getCondicionIva().getId().equals(condicionIvaSeleccionada.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La condición de IVA no coincide con la registrada para el cliente");
        }
        return cliente.getCondicionIva();
    }

    static void validarFacturaEditable(FacturaVenta factura) {
        if (!factura.isEditable()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede editar un comprobante autorizado, un X reemplazado por una factura autorizada, un comprobante anulado o un intento fiscal bloqueado");
        }
    }

    @Transactional(readOnly = true)
    public FacturaComprobanteDTO obtenerComprobante(Long id) {
        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));

        boolean comprobanteAutorizado = !factura.isComprobanteInterno() && factura.isComprobanteAfipDisponible()
                && factura.getCae() != null
                && !factura.getCae().isBlank()
                && "A".equals(factura.getResultadoAfip())
                && factura.getCaeFechaVencimiento() != null
                && factura.getNumero() != null
                && factura.getAmbienteAfip() != null
                && factura.getEmisorDenominacionAfip() != null
                && factura.getEmisorCuitAfip() != null
                && factura.getEmisorCondicionIvaAfip() != null
                && factura.getEmisorDomicilioAfip() != null
                && factura.getReceptorDenominacionAfip() != null
                && factura.getReceptorTipoDocumentoAfip() != null
                && factura.getReceptorNumeroDocumentoAfip() != null
                && factura.getPuntoVentaNumeroAfip() != null
                && factura.getTipoComprobanteAfip() != null
                && factura.getCodigoMonedaAfip() != null
                && factura.getCotizacionMonedaAfip() != null;

        List<FacturaComprobanteDTO.Detalle> detalles = factura.getDetalles().stream()
                .map(detalle -> new FacturaComprobanteDTO.Detalle(
                        detalle.getDescripcion(),
                        detalle.getCantidad(),
                        detalle.getPrecioUnitario(),
                        detalle.getPorcentajeBonificacion(),
                        detalle.getImporteNeto(),
                        detalle.getImporteIva(),
                        detalle.getImporteSubtotal()))
                .toList();

        double importeNeto = detalles.stream()
                .mapToDouble(FacturaComprobanteDTO.Detalle::importeNeto)
                .sum();
        double importeIva = detalles.stream()
                .mapToDouble(FacturaComprobanteDTO.Detalle::importeIva)
                .sum();

        Cliente cliente = factura.getCliente();
        String emisorCuit = comprobanteAutorizado
                ? factura.getEmisorCuitAfip()
                : emisorComprobanteProperties.getCuit();
        String receptorCuit = factura.isReceptorPrueba() ? null : comprobanteAutorizado
                ? factura.getReceptorCuitCuilAfip()
                : cliente == null ? null : cliente.getCuitCuil();
        String receptorDigitos = receptorCuit == null ? "" : receptorCuit.replaceAll("\\D", "");
        return new FacturaComprobanteDTO(
                factura.getId(),
                comprobanteAutorizado ? factura.getAmbienteAfip() : ambienteAfip,
                comprobanteAutorizado
                        ? factura.getEmisorDenominacionAfip()
                        : emisorComprobanteProperties.getDenominacion(),
                comprobanteAutorizado ? factura.getEmisorCuitAfip() : emisorCuit,
                comprobanteAutorizado
                        ? factura.getEmisorCondicionIvaAfip()
                        : emisorComprobanteProperties.getCondicionIva(),
                comprobanteAutorizado
                        ? factura.getEmisorDomicilioAfip()
                        : emisorComprobanteProperties.getDomicilio(),
                factura.isReceptorPrueba() ? "Consumidor Final" : comprobanteAutorizado
                        ? factura.getReceptorDenominacionAfip()
                        : cliente == null ? "Consumidor Final" : cliente.getDenominacion(),
                receptorCuit,
                factura.isReceptorPrueba() ? null : comprobanteAutorizado
                        ? factura.getReceptorDomicilioAfip()
                        : obtenerDomicilioCliente(cliente),
                factura.isReceptorPrueba() ? 99 : comprobanteAutorizado
                        ? factura.getReceptorTipoDocumentoAfip()
                        : cliente == null ? 99 : receptorDigitos.length() == 11 ? 80 : 96,
                factura.isReceptorPrueba() ? 0L : comprobanteAutorizado
                        ? factura.getReceptorNumeroDocumentoAfip()
                        : receptorDigitos.length() == 11 ? Long.parseLong(receptorDigitos) : 0L,
                factura.isReceptorPrueba() ? "Consumidor Final" : comprobanteAutorizado
                        ? factura.getReceptorCondicionIvaAfip()
                        : factura.getCondicionIva().getDenominacion(),
                comprobanteAutorizado
                        ? factura.getPuntoVentaNumeroAfip()
                        : factura.getPuntoVenta().getNumero(),
                comprobanteAutorizado
                        ? factura.getPuntoVentaDomicilioAfip()
                        : factura.getPuntoVenta().getDomicilioComercial(),
                factura.getTipoComprobanteAfip() != null ? factura.getTipoComprobanteAfip()
                        : ReceptorArcaPolicy.tipoComprobante(factura.getCondicionIva().getCodigoAfip()),
                factura.getNumero(),
                factura.getFechaEmision(),
                detalles,
                importeNeto,
                importeIva,
                factura.getImporteTotal(),
                comprobanteAutorizado
                        ? factura.getCodigoMonedaAfip()
                        : factura.getTipoMoneda().getCodigoAfip(),
                comprobanteAutorizado ? factura.getCotizacionMonedaAfip() : 1.0,
                comprobanteAutorizado ? factura.getCae() : null,
                comprobanteAutorizado ? factura.getCaeFechaVencimiento() : null,
                factura.getEstado(), factura.isReceptorPrueba());
    }

    public synchronized long obtenerSiguienteNumero() {
        long siguiente = Math.max(1L, facturaVentaRepository.obtenerNumeroMaximo() + 1L);
        while (facturaVentaRepository.existsByNumero(siguiente)) {
            siguiente++;
        }
        return siguiente;
    }

    @Transactional(readOnly = true)
    public long consultarSiguienteNumeroInterno(Long puntoVentaId) {
        if (puntoVentaId == null || !puntoVentaRepository.existsById(puntoVentaId))
            throw new IllegalArgumentException("No existe el punto de venta indicado");
        Long ultimo = facturaVentaRepository.obtenerUltimoNumeroInterno(puntoVentaId);
        return ultimo == null ? 1L : Math.addExact(ultimo, 1L);
    }

    @Transactional(readOnly = true)
    public long consultarSiguienteNumeroArca(Long puntoVentaId) {
        return consultarSiguienteNumeroArca(puntoVentaId, null);
    }

    @Transactional(readOnly = true)
    public long consultarSiguienteNumeroArca(Long puntoVentaId, Long condicionIvaId) {
        int tipo = condicionIvaId == null ? 6 : ReceptorArcaPolicy.tipoComprobante(
                condicionIvaRepository.findById(condicionIvaId).orElseThrow(() ->
                        new IllegalArgumentException("No existe la condicion de IVA indicada")).getCodigoAfip());
        PuntoVenta puntoVenta = puntoVentaRepository.findById(puntoVentaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No existe el punto de venta indicado"));
        try {
            long cuitEmisor = emisorComprobanteProperties.obtenerCuitNumerico();
            String[] tokenSign = afipWsaaService.obtenerTokenYSign(
                    keyPath, certPath);
            int ultimoAutorizado = afipWsfeService.obtenerUltimoComprobante(
                    tokenSign[0],
                    tokenSign[1],
                    cuitEmisor,
                    puntoVenta.getNumero(),
                    tipo);
            return (long) ultimoAutorizado + 1L;
        } catch (Exception exception) {
            log.warn("No se pudo consultar la numeracion ARCA: {}", exception.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo obtener la numeración actual de ARCA para el punto de venta "
                            + puntoVenta.getNumero(),
                    exception);
        }
    }

    @Transactional
    public synchronized FacturaVenta crearFactura(FacturaRequestDTO request) {
        LocalDate hoy = fechas.hoy();
        return crearFacturaConFechas(request, hoy, hoy);
    }

    private FacturaVenta crearFacturaConFechas(FacturaRequestDTO request, LocalDate fechaOperacion, LocalDate fechaFiscal) {
        return crearFacturaConFechas(request, fechaOperacion, fechaFiscal, new FacturaVenta());
    }

    /** Explicit one-time homologation of untouched local examples, using the ordinary creation flow. */
    @Transactional
    public FacturaVenta emitirMuestraInicial(Long id) {
        FacturaVenta muestra = facturaVentaRepository.buscarPorIdConBloqueo(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la muestra inicial"));
        if (!"EMITIDA".equals(muestra.getEstado()) || !"SIMULADO".equals(muestra.getResultadoAfip())
                || !"Factura de prueba".equals(muestra.getObservaciones())
                || muestra.getNumero() == null || muestra.getNumero() < 1001 || muestra.getNumero() > 1050
                || muestra.getCae() != null || muestra.getTipoComprobanteAfip() != null
                || muestra.getAmbienteAfip() != null || muestra.getFechaSolicitudAfip() != null
                || muestra.isComprobanteAfipDisponible() || muestra.getEmisorCuitAfip() != null
                || muestra.getOrigenComprobante() != null || muestra.getComprobanteFiscalDerivado() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La muestra ya fue procesada o no es un ejemplo local sin emitir");
        var detalles = muestra.getDetalles().stream().map(d -> new FacturaDetalleRequestDTO(
                d.getListaPrecioArticulo().getArticulo().getId(), d.getListaPrecioArticulo().getId(),
                d.getDescripcion(), d.getCantidad(), d.getPorcentajeBonificacion())).toList();
        var request = new FacturaRequestDTO(null, null,
                muestra.getCliente() == null ? null : muestra.getCliente().getId(), muestra.getCondicionIva().getId(),
                muestra.getTipoMoneda().getId(), muestra.getPuntoVenta().getId(), null,
                "Carga inicial enviada a ARCA homologacion", detalles);
        muestra.getDetalles().clear();
        muestra.setNumero(null);
        muestra.setResultadoAfip(null);
        LocalDate hoy = fechas.hoy();
        return crearFacturaConFechas(request, hoy, hoy, muestra);
    }

    private FacturaVenta crearFacturaConFechas(FacturaRequestDTO request, LocalDate fechaOperacion, LocalDate fechaFiscal,
            FacturaVenta factura) {

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

        requestValidation.validate(request);
        if (!request.isComprobanteInterno()) fechas.validar(fechaOperacion, fechaFiscal);

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
        condicionIva = aplicarCondicionIvaCliente(cliente, condicionIva);

        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));

        // 2. Construir la cabecera de la factura.
        LocalDate hoy = fechas.hoy();

        factura.setFechaEmision(fechaFiscal);
        factura.setFechaOperacion(request.isComprobanteInterno() ? null : fechaOperacion);
        factura.setCliente(cliente);
        factura.setCondicionIva(condicionIva);
        factura.setTipoMoneda(tipoMoneda);
        factura.setPuntoVenta(puntoVenta);
        factura.setObservaciones(request.getObservaciones());

        factura.setFechaAlta(hoy);
        factura.setFechaModificacion(hoy);
        factura.setUsuarioCarga(usuario);
        factura.setUsuarioModificacion(usuario);

        // 3. Construir los detalles y calcular sus importes.
        double total = 0.0;

        for (FacturaDetalleRequestDTO detalleRequest : request.getDetalles()) {

            if (detalleRequest == null || detalleRequest.getArticuloId() == null) {
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

            if (!Double.isFinite(bonificacion) || bonificacion < 0 || bonificacion > 100) {
                throw new IllegalArgumentException(
                        "La bonificación debe estar entre 0 y 100");
            }

            ListaPrecioArticulo precioArticulo = resolverPrecioArticulo(detalleRequest);

            double precioUnitario = precioArticulo.getPrecioVenta();

            double neto = calcularNeto(detalleRequest.getCantidad(), precioUnitario, bonificacion);

            double iva = request.isComprobanteInterno() ? 0.0 : BigDecimal.valueOf(neto).multiply(new BigDecimal("0.21"))
                    .setScale(2, RoundingMode.HALF_UP).doubleValue();

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

        // 4. Totales
        total = redondear(total);
        factura.setImporteTotal(total);
        factura.setImporteCobrado(total);
        factura.setImporteSaldo(0.0);

        if (!"HOMOLOGACION".equalsIgnoreCase(ambienteAfip)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La emisión de comprobantes está habilitada únicamente en homologación");
        }

        if (puntoVenta.getNumero() <= 0) {
            throw new IllegalArgumentException("El punto de venta debe tener un numero positivo");
        }
        if (!"PES".equals(tipoMoneda.getCodigoAfip())) {
            throw new IllegalArgumentException("La emision ARCA admite unicamente pesos argentinos (PES)");
        }
        if (request.isComprobanteInterno()) {
            // Lock the local X stream through commit; it never shares ARCA's A/B numbering.
            bloqueos.bloquearFlujo("COMPROBANTE_X:" + puntoVenta.getId());
            factura.setTipoComprobanteAfip(0);
            factura.setNumero(consultarSiguienteNumeroInterno(puntoVenta.getId()));
            factura.setEstado("EMITIDA");
            return facturaVentaRepository.save(factura);
        }
        ReceptorArcaPolicy.Receptor receptor = receptorArcaPolicy.resolver(cliente, condicionIva);
        factura.setReceptorPrueba(false);
        factura.setTipoComprobanteAfip(ReceptorArcaPolicy.tipoComprobante(receptor.condicionIvaId()));

        // 5. AUTENTICACIÓN Y EMISIÓN ANTE ARCA / AFIP
        try {
            int ptoVta = puntoVenta.getNumero();
            int cbteTipo = ReceptorArcaPolicy.tipoComprobante(receptor.condicionIvaId());
            long cuitEmisor = emisorComprobanteProperties.obtenerCuitNumerico();


            // 5a. Obtener Token y Sign
            String[] tokenSign = afipWsaaService.obtenerTokenYSign(keyPath, certPath);
            String token = tokenSign[0];
            String sign = tokenSign[1];

            // The Java monitor ends before the transactional proxy commits. A database
            // transaction lock protects this issuer/POS/type stream until commit.
            bloqueos.bloquearFlujo(cuitEmisor + ":" + ptoVta + ":" + cbteTipo);

            // 5b. Obtener el próximo número autorizado de ARCA
            int ultimoCbte = afipWsfeService.obtenerUltimoComprobante(token, sign, cuitEmisor, ptoVta, cbteTipo);
            if (ultimoCbte > 0) {
                LocalDate ultimaFecha = afipWsfeService.obtenerFechaComprobanteAutorizado(token, sign, cuitEmisor, ptoVta, cbteTipo, ultimoCbte);
                try { fechas.validarContraUltimoAutorizado(fechaFiscal, ultimaFecha); }
                catch (IllegalArgumentException invalida) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalida.getMessage(), invalida); }
            }
            // Recheck the calendar window after authentication/network waits, before sending a fiscal request.
            try { fechas.validar(fechaOperacion, fechaFiscal); }
            catch (IllegalArgumentException invalida) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalida.getMessage(), invalida); }
            int proximoNumeroAfip = ultimoCbte + 1;
            factura.setNumero((long) proximoNumeroAfip);

            // 5c. Datos del cliente/receptor
            int condicionIvaReceptor = receptor.condicionIvaId();

            // 5d. Emitir comprobante en WSFE
            factura.setFechaSolicitudAfip(fechas.ahora());
            String[] resAfip = afipWsfeService.solicitarCaeFactura(
                    token, sign, cuitEmisor, ptoVta, proximoNumeroAfip, cbteTipo,
                    receptor.tipoDocumento(), receptor.numeroDocumento(), total,
                    redondear(factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteNeto).sum()),
                    redondear(factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteIva).sum()),
                    condicionIvaReceptor, factura.getFechaEmision()
            );

            String cae = resAfip[0];
            String caeFchVto = resAfip[1];

            factura.setCae(cae);
            factura.setResultadoAfip("A");
            factura.setEstado("AUTORIZADA");
            factura.setAmbienteAfip(ambienteAfip);
            factura.setEmisorCuitAfip(String.valueOf(cuitEmisor));
            if (emisorComprobanteProperties.tieneDatosCompletos()) {
                factura.setEmisorDenominacionAfip(
                        emisorComprobanteProperties.getDenominacion().trim());
                factura.setEmisorCondicionIvaAfip(
                        emisorComprobanteProperties.getCondicionIva().trim());
                factura.setEmisorDomicilioAfip(
                        emisorComprobanteProperties.getDomicilio().trim());
            }
            factura.setReceptorDenominacionAfip(
                    receptor.denominacion());
            factura.setReceptorCuitCuilAfip(
                    receptor.cuitCuil());
            factura.setReceptorDomicilioAfip(receptor.prueba() ? null : obtenerDomicilioCliente(cliente));
            factura.setReceptorTipoDocumentoAfip(receptor.tipoDocumento());
            factura.setReceptorNumeroDocumentoAfip(receptor.numeroDocumento());
            factura.setReceptorCondicionIvaAfip(receptor.condicionIva());
            factura.setPuntoVentaNumeroAfip(ptoVta);
            factura.setPuntoVentaDomicilioAfip(puntoVenta.getDomicilioComercial());
            factura.setTipoComprobanteAfip(cbteTipo);
            factura.setCodigoMonedaAfip("PES");
            factura.setCotizacionMonedaAfip(1.0);
            if (caeFchVto != null && !caeFchVto.isBlank()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                factura.setCaeFechaVencimiento(LocalDate.parse(caeFchVto, formatter));
            }
            factura.setComprobanteAfipDisponible(
                    emisorComprobanteProperties.tieneDatosCompletos());

        } catch (ResponseStatusException invalida) {
            throw invalida;
        } catch (Exception e) {
            log.warn("No se pudo autorizar la factura en ARCA: {}", e.getMessage());

            if (factura.getCae() == null) {
                factura.setNumero(null);
                factura.setEstado(e instanceof ArcaRechazoException ? "RECHAZADA" : "ERROR_AFIP");
                if (e instanceof ArcaRechazoException) factura.setResultadoAfip("R");
            }
            String msg = e.getMessage() != null ? e.getMessage() : "Error desconocido";
            factura.setMotivoRechazo(msg.length() > 250 ? msg.substring(0, 250) : msg);
        }

        // 6. Guardar la factura en PostgreSQL
        return facturaVentaRepository.save(factura);
    }

    private String obtenerDomicilioCliente(Cliente cliente) {
        if (cliente == null || cliente.getDomicilio() == null) {
            return null;
        }

        String calle = cliente.getDomicilio().getNombreCalle();
        String numero = cliente.getDomicilio().getNumeroCalle();
        if (calle == null || calle.isBlank()) {
            return numero;
        }
        if (numero == null || numero.isBlank()) {
            return calle;
        }
        return calle.trim() + " " + numero.trim();
    }

    @Transactional
    public FacturaVenta emitirFiscalDesde(Long id, FacturaRequestDTO request) {
        FacturaVenta origen = facturaVentaRepository.buscarPorIdConBloqueo(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el comprobante de origen"));
        if (!origen.isPuedeEmitirFiscal()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El comprobante no permite una nueva emision fiscal o ya tiene un intento vinculado");
        if (request == null || request.isComprobanteInterno()) throw new IllegalArgumentException(
                "La nueva emision debe ser fiscal A/B; no se puede convertir un comprobante fiscal en X");
        if (request.getVersion() == null || !request.getVersion().equals(origen.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El comprobante de origen cambio. Recarga el formulario antes de emitir");
        requestValidation.validate(request);
        // Creation revalidates all local input and resolves current authoritative prices before contacting ARCA.
        LocalDate operacion = request.getFechaOperacion() != null ? request.getFechaOperacion()
                : origen.getFechaOperacion() != null ? origen.getFechaOperacion() : origen.getFechaEmision();
        LocalDate emision = request.getFechaEmision() != null ? request.getFechaEmision() : operacion;
        fechas.validar(operacion, emision);
        var nuevaSolicitud = new FacturaRequestDTO(null, emision, request.getClienteId(), request.getCondicionIvaId(),
                request.getTipoMonedaId(), request.getPuntoVentaId(), "EMITIDA", request.getObservaciones(), request.getDetalles());
        var fiscal = crearFacturaConFechas(nuevaSolicitud, operacion, emision);
        fiscal.setOrigenComprobante(origen);
        origen.setComprobanteFiscalDerivado(fiscal);
        if ("A".equals(fiscal.getResultadoAfip()) && fiscal.getCae() != null && !fiscal.getCae().isBlank()) {
            var antecedente = origen;
            var visitados = new java.util.HashSet<Long>();
            while (antecedente != null && visitados.add(antecedente.getId())) {
                if (antecedente.isComprobanteInterno()) antecedente.setReemplazadoPorFiscal(true);
                antecedente = antecedente.getOrigenComprobante();
            }
        }
        origen.setFechaModificacion(fechas.hoy());
        origen.setUsuarioModificacion(fiscal.getUsuarioCarga());
        facturaVentaRepository.flush();
        return fiscal;
    }

    @Transactional
    public FacturaVenta actualizarFactura(Long id, FacturaRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException("Los datos de la factura son obligatorios");
        }

        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));
        validarFacturaEditable(factura);
        if (request.getVersion() != null && !request.getVersion().equals(factura.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El comprobante cambio desde que lo abriste. Recarga el formulario antes de guardar");
        }
        if (factura.isComprobanteInterno() != request.isComprobanteInterno()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La clase no se cambia editando. Para X a A/B utiliza la accion de emitir fiscal; A/B a X no esta permitido");
        }
        if (factura.isComprobanteInterno() && !factura.getPuntoVenta().getId().equals(request.getPuntoVentaId())) {
            throw new IllegalArgumentException("El punto de venta de un comprobante X emitido no se puede cambiar");
        }
        requestValidation.validate(request);

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
        if (puntoVenta.getNumero() <= 0) throw new IllegalArgumentException("El punto de venta debe tener un numero positivo");
        if (!"PES".equals(tipoMoneda.getCodigoAfip()))
            throw new IllegalArgumentException("El proyecto admite unicamente pesos argentinos (PES)");
        condicionIva = aplicarCondicionIvaCliente(cliente, condicionIva);
        if (!factura.isComprobanteInterno()) receptorArcaPolicy.resolver(cliente, condicionIva);


        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));

        LocalDate hoy = fechas.hoy();

        factura.setCliente(cliente);
        factura.setCondicionIva(condicionIva);
        factura.setTipoMoneda(tipoMoneda);
        factura.setPuntoVenta(puntoVenta);

        if (request.getEstado() != null && !request.getEstado().isBlank()
                && !request.getEstado().equals(factura.getEstado())) {
            throw new IllegalArgumentException("El estado no se cambia editando; se determina por la autorizacion o las acciones del comprobante");
        }

        if (!factura.isComprobanteInterno()) {
            factura.setTipoComprobanteAfip(ReceptorArcaPolicy.tipoComprobante(condicionIva.getCodigoAfip()));
        }
        factura.setObservaciones(request.getObservaciones());
        factura.setFechaModificacion(hoy);
        factura.setUsuarioModificacion(usuario);

        factura.getDetalles().clear();

        double total = 0.0;

        for (FacturaDetalleRequestDTO detalleRequest : request.getDetalles()) {

            if (detalleRequest == null || detalleRequest.getArticuloId() == null) {
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

            if (!Double.isFinite(bonificacion) || bonificacion < 0 || bonificacion > 100) {
                throw new IllegalArgumentException(
                        "La bonificación debe estar entre 0 y 100");
            }

            ListaPrecioArticulo precioArticulo = resolverPrecioArticulo(detalleRequest);

            double precioUnitario = precioArticulo.getPrecioVenta();

            double neto = calcularNeto(detalleRequest.getCantidad(), precioUnitario, bonificacion);

            double iva = request.isComprobanteInterno() ? 0.0 : BigDecimal.valueOf(neto).multiply(new BigDecimal("0.21"))
                    .setScale(2, RoundingMode.HALF_UP).doubleValue();
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

        total = redondear(total);
        factura.setImporteTotal(total);
        factura.setImporteCobrado(total);
        factura.setImporteSaldo(0.0);

        return facturaVentaRepository.save(factura);
    }

    @Transactional
    public void anularFactura(Long id) {

        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));

        if ("ANULADA".equalsIgnoreCase(factura.getEstado())
                || factura.getFechaAnulacion() != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "La factura ya está anulada");
        }

        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));

        LocalDate hoy = fechas.hoy();

        factura.setEstadoAnteriorAnulacion(factura.getEstado());
        factura.setEstado("ANULADA");
        factura.setFechaAnulacion(hoy);
        factura.setFechaBaja(hoy);
        factura.setFechaModificacion(hoy);
        factura.setUsuarioModificacion(usuario);
        factura.setUsuarioBaja(usuario);

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

        factura.setEstado(factura.estadoAlReactivar());
        factura.setEstadoAnteriorAnulacion(null);
        limpiarDatosBaja(factura);
        factura.setFechaModificacion(fechas.hoy());
        factura.setUsuarioModificacion(usuario);
        facturaVentaRepository.save(factura);
    }

    private void limpiarDatosBaja(FacturaVenta factura) {
        factura.setFechaAnulacion(null);
        factura.setFechaBaja(null);
        factura.setUsuarioBaja(null);
    }

    private static void validarEstadoFiltro(String estado) {
        if (estado != null && !estado.isBlank() && !java.util.Set.of(
                "EMITIDA", "AUTORIZADA", "RECHAZADA", "ERROR_AFIP", "ANULADA", "PENDIENTE", "PAGADA").contains(estado)) {
            throw new IllegalArgumentException("Estado de factura no valido");
        }
    }

    static void validarFiltros(LocalDate desde, LocalDate hasta, Double minimo,
            Long clienteId, boolean sinCliente, Long numero, Long numeroDesde, Long numeroHasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
        }
        if (minimo != null && (!Double.isFinite(minimo) || minimo < 0)) {
            throw new IllegalArgumentException("El importe minimo debe ser finito y no negativo");
        }
        if (clienteId != null && (clienteId <= 0 || sinCliente)) {
            throw new IllegalArgumentException("El cliente debe ser positivo y no puede combinarse con sinCliente");
        }
        for (Long valor : new Long[]{numero, numeroDesde, numeroHasta}) {
            if (valor != null && valor < 0) {
                throw new IllegalArgumentException("Los numeros de comprobante deben ser no negativos");
            }
        }
        if (numeroDesde != null && numeroHasta != null && numeroDesde > numeroHasta) {
            throw new IllegalArgumentException("El numero desde no puede superar al numero hasta");
        }
        if (numero != null && ((numeroDesde != null && numero < numeroDesde)
                || (numeroHasta != null && numero > numeroHasta))) {
            throw new IllegalArgumentException("El numero exacto debe estar dentro del rango");
        }
    }

    static double calcularNeto(double cantidad, double precio, double bonificacion) {
        return BigDecimal.valueOf(cantidad).multiply(BigDecimal.valueOf(precio))
                .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(bonificacion).movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double redondear(double importe) {
        if (!Double.isFinite(importe)) {
            throw new IllegalArgumentException("El importe calculado excede el limite permitido");
        }
        return BigDecimal.valueOf(importe)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private ListaPrecioArticulo resolverPrecioArticulo(FacturaDetalleRequestDTO detalleRequest) {
        if (detalleRequest.getListaPrecioArticuloId() == null) {
            throw new IllegalArgumentException(
                    "Cada detalle debe indicar el precio de venta seleccionado");
        }

        ListaPrecioArticulo precioArticulo = listaPrecioArticuloRepository
                .findById(detalleRequest.getListaPrecioArticuloId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el precio de venta seleccionado"));

        if (!precioArticulo.getArticulo().getId().equals(detalleRequest.getArticuloId())) {
            throw new IllegalArgumentException(
                    "El precio seleccionado no pertenece al artículo indicado");
        }
        if (!Double.isFinite(precioArticulo.getPrecioVenta())
                || precioArticulo.getPrecioVenta() <= 0) {
            throw new IllegalArgumentException(
                    "El precio de venta seleccionado debe ser mayor que cero");
        }
        return precioArticulo;
    }

}
