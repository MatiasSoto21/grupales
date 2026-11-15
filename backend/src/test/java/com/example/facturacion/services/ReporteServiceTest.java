package com.example.facturacion.services;

import com.example.facturacion.dto.FacturaReporteDTO;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;

class ReporteServiceTest {
    @Test
    void unnumberedRejectedInvoiceCanBeExported() throws Exception {
        var invoice = new FacturaReporteDTO(1L, null, LocalDate.now(), "Consumidor Final", "Consumidor Final",
                "Central", 121, 1, "ERROR_AFIP", false, false);
        var service = new ReporteService();
        byte[] excel = service.generarExcel(List.of(invoice));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(invoice.getReferenciaComprobante(), workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
        assertTrue(service.generarPdf(List.of(invoice)).length > 1000);
    }
}
