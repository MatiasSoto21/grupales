package com.example.facturacion.controllers;

import com.example.facturacion.dto.*;
import com.example.facturacion.services.FacturaService;
import com.example.facturacion.services.ComprobanteFacturaService;
import com.example.facturacion.services.ReporteService;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
public class FacturaRestController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final FacturaService facturaService;
    private final ReporteService reporteService;
    private final ComprobanteFacturaService comprobanteFacturaService;

    @GetMapping("/siguiente-numero")
    public Map<String, Long> siguienteNumero(@RequestParam Long puntoVentaId, @RequestParam(required = false) Long condicionIvaId,
            @RequestParam(defaultValue = "false") boolean comprobanteInterno) {
        return Map.of("numero", comprobanteInterno ? facturaService.consultarSiguienteNumeroInterno(puntoVentaId)
                : facturaService.consultarSiguienteNumeroArca(puntoVentaId, condicionIvaId));
    }
    @GetMapping
    public PageResponseDTO<FacturaReporteDTO> listar(@ModelAttribute FacturaFiltrosDTO filtros) { return facturaService.listar(filtros); }

    @GetMapping("/{id:[0-9]+}")
    public FacturaRequestDTO obtenerPorId(@PathVariable Long id) { return facturaService.obtenerFormulario(id); }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@RequestBody FacturaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facturaService.crearResumen(request));
    }
    @PutMapping("/{id:[0-9]+}")
    public FacturaReporteDTO actualizar(@PathVariable Long id, @RequestBody FacturaRequestDTO request) { return facturaService.actualizarResumen(id, request); }
    @PostMapping("/{id:[0-9]+}/emitir-fiscal")
    public ResponseEntity<FacturaReporteDTO> emitirFiscal(@PathVariable Long id, @RequestBody FacturaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facturaService.emitirFiscalResumen(id, request));
    }
    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) { facturaService.anularFactura(id); return ResponseEntity.noContent().build(); }
    @PostMapping("/{id:[0-9]+}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) { facturaService.reactivarFactura(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/{id:[0-9]+}/comprobante.pdf")
    public ResponseEntity<byte[]> descargarComprobante(@PathVariable Long id) {
        var comprobante = facturaService.obtenerComprobante(id);
        String nombre = (comprobante.tipoComprobante() == 0 ? "X" : "fiscal") + "-" + comprobante.puntoVentaNumero() + "-" + id;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=comprobante-" + nombre + ".pdf")
                .contentType(MediaType.APPLICATION_PDF).body(comprobanteFacturaService.generarPdf(comprobante));
    }
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> descargarPdf(@ModelAttribute FacturaFiltrosDTO filtros) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-facturas.pdf")
                .contentType(MediaType.APPLICATION_PDF).body(reporteService.generarPdf(facturaService.buscarParaReporte(filtros)));
    }
    @GetMapping("/excel")
    public ResponseEntity<byte[]> descargarExcel(@ModelAttribute FacturaFiltrosDTO filtros) throws IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-facturas.xlsx")
                .contentType(XLSX).body(reporteService.generarExcel(facturaService.buscarParaReporte(filtros)));
    }
}
