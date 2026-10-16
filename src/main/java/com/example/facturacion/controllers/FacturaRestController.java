package com.example.facturacion.controllers;

import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.dto.FacturaRequestDTO;
import com.example.facturacion.entities.FacturaVenta;
import com.example.facturacion.services.FacturaService;
import com.example.facturacion.services.ReporteService;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
public class FacturaRestController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FacturaService facturaService;
    private final ReporteService reporteService;

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

        List<FacturaReporteDTO> facturas =
                facturaService.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo);

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

        List<FacturaReporteDTO> facturas =
                facturaService.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-facturas.xlsx")
                .contentType(XLSX)
                .body(reporteService.generarExcel(facturas));
    }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@RequestBody FacturaRequestDTO request) {
        FacturaVenta factura = facturaService.crearFactura(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertirAReporteDTO(factura));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> actualizar(@PathVariable Long id, @RequestBody FacturaRequestDTO request) {
        FacturaVenta factura = facturaService.actualizarFactura(id, request);
        return ResponseEntity.ok(convertirAReporteDTO(factura));
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<FacturaReporteDTO> anular(@PathVariable Long id) {
        FacturaVenta factura = facturaService.anularFactura(id);
        return ResponseEntity.ok(convertirAReporteDTO(factura));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        facturaService.eliminarFactura(id);
        return ResponseEntity.noContent().build();
    }

    private FacturaReporteDTO convertirAReporteDTO(FacturaVenta factura) {
        return new FacturaReporteDTO(
                factura.getNumero(),
                factura.getFechaEmision(),
                factura.getCliente() != null
                        ? factura.getCliente().getDenominacion()
                        : "Consumidor Final",
                factura.getCondicionIva().getDenominacion(),
                factura.getPuntoVenta().getDescripcion(),
                factura.getImporteTotal(),
                factura.getDetalles().size()
        );
    }
}
