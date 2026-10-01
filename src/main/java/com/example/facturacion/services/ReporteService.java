package com.example.facturacion.services;

import com.example.facturacion.dto.FacturaReporteDTO;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Service
public class ReporteService {

    private static final String[] ENCABEZADOS = {
            "Nº Factura", "Fecha", "Cliente", "Condición IVA",
            "Punto de venta", "Importe total", "Ítems"
    };

    public byte[] generarPdf(List<FacturaReporteDTO> facturas) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(out));

        try (Document doc = new Document(pdf, PageSize.A4.rotate())) {
            doc.add(new Paragraph("Reporte ejecutivo de ventas").setBold().setFontSize(16));

            Table tabla = new Table(UnitValue.createPercentArray(new float[]{1, 1.2f, 2.5f, 2.5f, 2, 1.3f, 1}))
                    .useAllAvailableWidth();

            for (String encabezado : ENCABEZADOS) {
                tabla.addHeaderCell(new Cell().add(new Paragraph(encabezado).setBold()));
            }

            for (FacturaReporteDTO f : facturas) {
                tabla.addCell(String.valueOf(f.getNumeroFactura()));
                tabla.addCell(String.valueOf(f.getFechaEmision()));
                tabla.addCell(Objects.toString(f.getClienteDenominacion(), ""));
                tabla.addCell(Objects.toString(f.getCondicionIva(), ""));
                tabla.addCell(Objects.toString(f.getPuntoVentaDescripcion(), ""));
                tabla.addCell(String.format("%.2f", f.getImporteTotal()));
                tabla.addCell(String.valueOf(f.getCantidadItems()));
            }

            doc.add(tabla);
        }
        return out.toByteArray();
    }

    public byte[] generarExcel(List<FacturaReporteDTO> facturas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet hoja = libro.createSheet("Facturas");

            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                cabecera.createCell(i).setCellValue(ENCABEZADOS[i]);
            }

            int fila = 1;
            for (FacturaReporteDTO f : facturas) {
                Row row = hoja.createRow(fila++);
                row.createCell(0).setCellValue(f.getNumeroFactura());
                row.createCell(1).setCellValue(String.valueOf(f.getFechaEmision()));
                row.createCell(2).setCellValue(f.getClienteDenominacion());
                row.createCell(3).setCellValue(f.getCondicionIva());
                row.createCell(4).setCellValue(f.getPuntoVentaDescripcion());
                row.createCell(5).setCellValue(f.getImporteTotal());
                row.createCell(6).setCellValue(f.getCantidadItems());
            }

            for (int i = 0; i < ENCABEZADOS.length; i++) {
                hoja.autoSizeColumn(i);
            }

            libro.write(out);
            return out.toByteArray();
        }
    }
}
