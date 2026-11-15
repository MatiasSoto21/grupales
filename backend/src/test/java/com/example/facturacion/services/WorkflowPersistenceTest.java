package com.example.facturacion.services;

import com.example.facturacion.FacturacionApplication;
import com.example.facturacion.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.math.*;
import java.nio.file.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "workflow.report", matches = ".+")
@SpringBootTest(classes = FacturacionApplication.class, properties = {
    "debug=false", "facturacion.arca-test.enabled=false", "logging.level.root=WARN"
})
class WorkflowPersistenceTest {
    @Autowired FacturaVentaRepository invoices;
    @Autowired ClienteRepository clients;
    @Autowired FacturaService invoiceService;
    @Autowired ComprobanteFacturaService pdfService;

    @Test
    @Transactional(readOnly = true)
    void persistedRealWorkflowMatchesUiMoneyAuditAndReceipt() throws Exception {
        Path reportPath = Path.of(System.getProperty("workflow.report"));
        var report = new ObjectMapper().readTree(Files.readString(reportPath));
        var clientData = report.get("client");
        var client = clients.findById(clientData.get("id").asLong()).orElseThrow();
        assertEquals(clientData.get("cuit").asText(), client.getCuitCuil());
        assertEquals(clientData.get("denominacion").asText(), client.getDenominacion());
        assertEquals(clientData.get("condicionIvaId").asLong(), client.getCondicionIva().getId());
        assertEquals("prueba-e2e@example.invalid", client.getContacto().getEmail());
        assertNotNull(client.getDomicilio());
        assertNotNull(client.getUsuarioCarga());
        assertNotNull(client.getFechaAlta());
        for (var invoiceData : report.get("invoices")) {
            var invoice = invoices.findById(invoiceData.get("id").asLong()).orElseThrow();
            assertEquals(invoiceData.get("expectedTotal").asDouble(), invoice.getImporteTotal(), 0.001);
            assertEquals(invoice.getImporteTotal(), invoice.getImporteCobrado(), 0.001);
            assertEquals(0.0, invoice.getImporteSaldo());
            assertEquals(LocalDate.parse(invoiceData.get("fechaEmision").asText()), invoice.getFechaEmision());
            assertTrue(invoice.getObservaciones().startsWith(clientData.get("marker").asText()));
            var expectedLines = invoiceData.get("expectedLines");
            assertEquals(expectedLines.size(), invoice.getDetalles().size());
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < expectedLines.size(); i++) {
                var expected = expectedLines.get(i);
                var line = invoice.getDetalles().get(i);
                assertEquals(expected.get("listaPrecioArticuloId").asLong(), line.getListaPrecioArticulo().getId());
                assertEquals(expected.get("articuloId").asLong(), line.getListaPrecioArticulo().getArticulo().getId());
                assertEquals(expected.get("precio").asDouble(), line.getPrecioUnitario());
                assertEquals(expected.get("cantidad").asDouble(), line.getCantidad());
                assertEquals(expected.get("porcentajeBonificacion").asDouble(), line.getPorcentajeBonificacion());
                BigDecimal net = BigDecimal.valueOf(line.getPrecioUnitario()).multiply(BigDecimal.valueOf(line.getCantidad()))
                        .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(line.getPorcentajeBonificacion()).movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal tax = net.multiply(new BigDecimal("0.21")).setScale(2, RoundingMode.HALF_UP);
                assertEquals(net.doubleValue(), line.getImporteNeto());
                assertEquals(tax.doubleValue(), line.getImporteIva());
                assertEquals(net.add(tax).doubleValue(), line.getImporteSubtotal());
                total = total.add(net).add(tax);
            }
            assertEquals(total.doubleValue(), invoice.getImporteTotal());
            if ("RECHAZADA".equals(invoiceData.path("estado").asText())) {
                assertEquals("RECHAZADA", invoice.getEstado()); assertEquals("R", invoice.getResultadoAfip());
                assertNull(invoice.getCae()); assertNull(invoice.getNumero());
                assertEquals(invoiceData.path("motivoRechazo").asText(), invoice.getMotivoRechazo());
                assertFalse(invoice.isComprobanteAfipDisponible());
            }
            var receipt = invoiceService.obtenerComprobante(invoice.getId());
            if (invoiceData.get("tieneCae").asBoolean()) {
                assertTrue(invoice.getCae().matches("[0-9]{14}"));
                assertNotNull(invoice.getCaeFechaVencimiento());
                assertEquals("A", invoice.getResultadoAfip());
                assertEquals("HOMOLOGACION", invoice.getAmbienteAfip());
                assertEquals(invoiceData.get("numeroFactura").asLong(), invoice.getNumero());
                assertEquals(6, invoice.getTipoComprobanteAfip());
                assertEquals("PES", invoice.getCodigoMonedaAfip());
                if (invoiceData.path("receptorPrueba").asBoolean()) {
                    assertTrue(invoice.isReceptorPrueba());
                    assertEquals(99, invoice.getReceptorTipoDocumentoAfip());
                    assertEquals(0L, invoice.getReceptorNumeroDocumentoAfip());
                    assertNull(invoice.getReceptorCuitCuilAfip());
                    assertEquals("Consumidor Final", invoice.getReceptorCondicionIvaAfip());
                    assertEquals(99, receipt.receptorTipoDocumento());
                    assertEquals(0L, receipt.receptorNumeroDocumento());
                }
                if (report.path("success").asBoolean() && invoice.getId().equals(report.get("authorizedInvoiceId").asLong())) {
                    assertEquals("EMITIDA", invoice.getEstado());
                    assertNull(invoice.getFechaAnulacion());
                    assertNull(invoice.getFechaBaja());
                    assertNull(invoice.getUsuarioBaja());
                }
                if (!invoice.isComprobanteAfipDisponible()) assertNull(receipt.cae());
                else {
                    assertEquals(invoice.getCae(), receipt.cae());
                    assertEquals("EMISOR DE PRUEBA - HOMOLOGACION", receipt.emisorDenominacion());
                    assertEquals("Domicilio de prueba 123", receipt.emisorDomicilio());
                    String url = pdfService.generarUrlQr(receipt);
                    String payload = java.net.URLDecoder.decode(url.substring(url.indexOf("?p=") + 3), java.nio.charset.StandardCharsets.UTF_8);
                    var qr = new ObjectMapper().readTree(java.util.Base64.getDecoder().decode(payload));
                    assertEquals(invoice.getNumero().longValue(), qr.get("nroCmp").asLong());
                    assertEquals(Long.parseLong(invoice.getCae()), qr.get("codAut").asLong());
                    assertEquals(invoice.getImporteTotal(), qr.get("importe").asDouble());
                    assertEquals(Long.parseLong(receipt.emisorCuit()), qr.get("cuit").asLong());
                    if (invoice.isReceptorPrueba()) {
                        assertEquals(99, qr.get("tipoDocRec").asInt());
                        assertEquals(0L, qr.get("nroDocRec").asLong());
                    }
                }
            }
            byte[] pdf = pdfService.generarPdf(receipt);
            try (var document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
                String text = PdfTextExtractor.getTextFromPage(document.getPage(1));
                assertTrue(text.contains("SIN VALIDEZ FISCAL"));
                if (receipt.receptorPrueba()) {
                    assertTrue(text.contains("RECEPTOR DE PRUEBA"));
                    assertFalse(text.contains(clientData.get("cuit").asText()));
                }
                if (receipt.cae() == null) assertTrue(text.contains("NO AUTORIZADA POR ARCA"));
                else {
                    assertTrue(text.contains(receipt.cae()));
                    assertFalse(text.contains("NO AUTORIZADA POR ARCA"));
                    assertNotNull(document.getPage(1).getResources().getResource(PdfName.XObject));
                }
            }
        }
        if (report.path("success").asBoolean()) {
            Path excel = reportPath.getParent().resolve("reporte.xlsx");
            try (var workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
                assertEquals(1, workbook.getSheetAt(0).getLastRowNum());
                var authorized = invoices.findById(report.get("authorizedInvoiceId").asLong()).orElseThrow();
                assertTrue(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue().endsWith(String.format("%08d", authorized.getNumero())));
                assertEquals(authorized.getImporteTotal(), workbook.getSheetAt(0).getRow(1).getCell(5).getNumericCellValue());
            }
        }
    }
}
