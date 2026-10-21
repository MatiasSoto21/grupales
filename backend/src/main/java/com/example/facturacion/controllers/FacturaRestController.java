package com.example.facturacion.controllers;

import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.dto.FacturaDetalleRequestDTO;
import com.example.facturacion.dto.FacturaRequestDTO;

import com.example.facturacion.repositories.FacturaVentaRepository;

import com.example.facturacion.services.FacturaService;
import com.example.facturacion.services.ReporteService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.facturacion.entities.FacturaVenta;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
//@CrossOrigin(origins = "http://localhost:3000")
public class FacturaRestController {

    private static final MediaType XLSX
            = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FacturaService facturaService;
    private final ReporteService reporteService;
    private final FacturaVentaRepository facturaVentaRepository;

    @GetMapping
    public List<FacturaReporteDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo) {

        return facturaService.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> descargarPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo) {

        List<FacturaReporteDTO> facturas
                = facturaService.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-facturas.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(reporteService.generarPdf(facturas));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> descargarExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo) throws IOException {

        List<FacturaReporteDTO> facturas
                = facturaService.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-facturas.xlsx")
                .contentType(XLSX)
                .body(reporteService.generarExcel(facturas));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaRequestDTO> obtenerPorId(
            @PathVariable Long id) {

        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe la factura con ID " + id));

        List<FacturaDetalleRequestDTO> detalles
                = factura.getDetalles().stream()
                        .map(d -> new FacturaDetalleRequestDTO(
                                d.getListaPrecioArticulo()
                                        .getArticulo().getId(),
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
                factura.getImporteCobrado(),
                factura.getImporteSaldo(),
                factura.getEstado(),
                factura.getObservaciones(),
                detalles);

        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(
            @RequestBody FacturaRequestDTO request) {

        FacturaVenta factura = facturaService.crearFactura(request);

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
                factura.getEstado()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(reporte);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> actualizar(
            @PathVariable Long id,
            @RequestBody FacturaRequestDTO request) {

        FacturaVenta factura = facturaService.actualizarFactura(id, request);

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
                factura.getEstado()
        );
        return ResponseEntity.ok(reporte);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {

        facturaService.anularFactura(id);

        return ResponseEntity.noContent().build();
    }

}
