package com.example.facturacion.services;

import com.example.facturacion.dto.FacturaComprobanteDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ComprobanteFacturaService {

    private static final String URL_QR_ARCA = "https://www.afip.gob.ar/fe/qr/?p=";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generarPdf(FacturaComprobanteDTO comprobante) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(out));

        try (Document document = new Document(pdf, PageSize.A4)) {
            document.setMargins(28, 32, 28, 32);
            document.setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA));

            document.add(new Paragraph("HOMOLOGACIÓN — SIN VALIDEZ FISCAL")
                    .setBold()
                    .setFontSize(13)
                    .setFontColor(ColorConstants.RED)
                    .setTextAlignment(TextAlignment.CENTER));

            if (comprobante.receptorPrueba()) {
                document.add(new Paragraph("RECEPTOR DE PRUEBA: CONSUMIDOR FINAL SIN IDENTIFICAR")
                        .setBold().setFontSize(10).setTextAlignment(TextAlignment.CENTER));
            }
            document.add(new Paragraph(comprobante.tipoComprobante() == 0 ? "COMPROBANTE X" : "FACTURA " + letraComprobante(comprobante.tipoComprobante()))
                    .setBold()
                    .setFontSize(19)
                    .setTextAlignment(TextAlignment.CENTER));

            if (comprobante.tipoComprobante() == 0) {
                document.add(new Paragraph("DOCUMENTO NO VALIDO COMO FACTURA")
                        .setBold().setTextAlignment(TextAlignment.CENTER));
            }
            Table emisor = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth();
            emisor.addCell(celda("EMISOR", true));
            emisor.addCell(celda("COMPROBANTE", true));
            emisor.addCell(celda(comprobante.emisorDenominacion(), false));
            emisor.addCell(celda("Punto de venta: %05d".formatted(
                    comprobante.puntoVentaNumero()), false));
            emisor.addCell(celda("CUIT: " + formatearCuit(comprobante.emisorCuit()), false));
            emisor.addCell(celda(comprobante.numero() == null ? "Numero: Sin asignar"
                    : "Numero: %s-%05d-%08d".formatted(comprobante.tipoComprobante() == 0 ? "X" : letraComprobante(comprobante.tipoComprobante()), comprobante.puntoVentaNumero(), comprobante.numero()), false));
            emisor.addCell(celda("IVA: " + valorOGuion(comprobante.emisorCondicionIva()), false));
            emisor.addCell(celda("Fecha de emisión: " + FECHA.format(comprobante.fechaEmision()), false));
            emisor.addCell(celda("Domicilio: " + valorOGuion(comprobante.emisorDomicilio()), false));
            emisor.addCell(celda("Domicilio punto de venta: "
                    + valorOGuion(comprobante.puntoVentaDomicilio()), false));
            document.add(emisor);

            Table receptor = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth()
                    .setMarginTop(12);
            receptor.addCell(celda("RECEPTOR", true));
            receptor.addCell(celda("CONDICIÓN FRENTE AL IVA", true));
            receptor.addCell(celda(comprobante.receptorDenominacion(), false));
            receptor.addCell(celda(comprobante.receptorCondicionIva(), false));
            receptor.addCell(celda("Documento: " + documentoReceptor(comprobante)
                    + "\nDomicilio: " + valorOGuion(comprobante.receptorDomicilio()), false));
            receptor.addCell(celda("Punto de venta: "
                    + valorOGuion(comprobante.puntoVentaDomicilio()), false));
            document.add(receptor);

            Table items = new Table(UnitValue.createPercentArray(
                    comprobante.tipoComprobante() == 0 ? new float[]{40, 10, 18, 12, 20} : new float[]{36, 8, 15, 11, 14, 16}))
                    .useAllAvailableWidth()
                    .setMarginTop(16);
            for (String heading : comprobante.tipoComprobante() == 0
                    ? new String[]{"Descripción", "Cant.", "Precio unit.", "Bonif. %", "Importe"}
                    : new String[]{"Descripción", "Cant.", "Precio unit.", "Bonif. %", "IVA", "Subtotal"}) {
                items.addHeaderCell(celda(heading, true));
            }
            for (FacturaComprobanteDTO.Detalle detalle : comprobante.detalles()) {
                items.addCell(celda(detalle.descripcion(), false));
                items.addCell(celda(formatNumber(detalle.cantidad()), false));
                items.addCell(celda(formatAmount(detalle.precioUnitario()), false));
                items.addCell(celda(formatNumber(detalle.porcentajeBonificacion()), false));
                if (comprobante.tipoComprobante() != 0) items.addCell(celda(formatAmount(detalle.importeIva()), false));
                items.addCell(celda(formatAmount(detalle.importeSubtotal()), false));
            }
            document.add(items);

            if (comprobante.tipoComprobante() != 0) {
                document.add(new Paragraph("Neto gravado: " + formatAmount(comprobante.importeNeto()))
                        .setTextAlignment(TextAlignment.RIGHT).setMarginTop(14));
                if (comprobante.tipoComprobante() == 6) {
                    document.add(new Paragraph("Regimen de Transparencia Fiscal al Consumidor Ley 27.743").setFontSize(9));
                    document.add(new Paragraph("IVA contenido: " + formatAmount(comprobante.importeIva()))
                            .setTextAlignment(TextAlignment.RIGHT));
                    document.add(new Paragraph("Otros impuestos nacionales indirectos: " + formatAmount(0))
                            .setTextAlignment(TextAlignment.RIGHT));
                } else {
                    document.add(new Paragraph("IVA: " + formatAmount(comprobante.importeIva()))
                            .setTextAlignment(TextAlignment.RIGHT));
                }
            }
            document.add(new Paragraph("TOTAL: " + formatAmount(comprobante.importeTotal()))
                    .setBold()
                    .setFontSize(15)
                    .setTextAlignment(TextAlignment.RIGHT));

            if (comprobante.tipoComprobante() == 0) {
                document.add(new Paragraph("DOCUMENTO NO VALIDO COMO FACTURA")
                        .setBold().setFontColor(ColorConstants.RED).setTextAlignment(TextAlignment.CENTER));
            } else if (comprobante.cae() == null || comprobante.cae().isBlank()) {
                document.add(new Paragraph("VISTA PREVIA — NO AUTORIZADA POR ARCA")
                        .setBold()
                        .setFontColor(ColorConstants.RED)
                        .setMarginTop(14));
            } else {
                document.add(new Paragraph("CAE: " + comprobante.cae())
                        .setBold()
                        .setMarginTop(14));
                document.add(new Paragraph("Vencimiento del CAE: "
                        + (comprobante.caeFechaVencimiento() == null
                        ? "—"
                        : FECHA.format(comprobante.caeFechaVencimiento()))));

                BarcodeQRCode qr = new BarcodeQRCode(generarUrlQr(comprobante));
                PdfFormXObject qrObject = qr.createFormXObject(ColorConstants.BLACK, pdf);
                document.add(new Image(qrObject).setWidth(112).setHeight(112).setMarginTop(8));
            }

            if ("ANULADA".equalsIgnoreCase(comprobante.estadoSistema())) {
                document.add(new Paragraph(
                        comprobante.tipoComprobante() == 0 ? "Comprobante interno anulado en el sistema."
                        : "Anulada en el sistema de gestión. Esta marca no anula ni revoca el CAE ante ARCA.")
                        .setBold()
                        .setFontColor(ColorConstants.RED)
                        .setMarginTop(8));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el PDF del comprobante", exception);
        }

        return out.toByteArray();
    }

    String generarUrlQr(FacturaComprobanteDTO comprobante) {
        if (comprobante.tipoComprobante() == 0 || comprobante.cae() == null || comprobante.cae().isBlank()) {
            throw new IllegalArgumentException("El comprobante no tiene autorizacion ARCA para generar QR");
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ver", 1);
        datos.put("fecha", comprobante.fechaEmision().toString());
        datos.put("cuit", Long.parseLong(comprobante.emisorCuit()));
        datos.put("ptoVta", comprobante.puntoVentaNumero());
        datos.put("tipoCmp", comprobante.tipoComprobante());
        datos.put("nroCmp", comprobante.numero());
        datos.put("importe", comprobante.importeTotal());
        datos.put("moneda", comprobante.moneda());
        datos.put("ctz", comprobante.cotizacionMoneda());
        datos.put("tipoDocRec", comprobante.receptorTipoDocumento());
        datos.put("nroDocRec", comprobante.receptorNumeroDocumento());
        datos.put("tipoCodAut", "E");
        datos.put("codAut", Long.parseLong(comprobante.cae()));

        try {
            String json = objectMapper.writeValueAsString(datos);
            String base64 = java.util.Base64.getEncoder()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
            return URL_QR_ARCA + URLEncoder.encode(base64, StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo generar el contenido del QR del comprobante", exception);
        }
    }

    private Cell celda(String texto, boolean encabezado) {
        Paragraph paragraph = new Paragraph(valorOGuion(texto)).setFontSize(9);
        if (encabezado) {
            paragraph.setBold();
        }
        return new Cell().add(paragraph);
    }

    private String letraComprobante(int tipo) {
        return switch (tipo) {
            case 1, 2, 3 -> "A";
            case 6, 7, 8 -> "B";
            case 11, 12, 13 -> "C";
            default -> "ELECTRÓNICA";
        };
    }

    private String documentoReceptor(FacturaComprobanteDTO comprobante) {
        if (comprobante.receptorTipoDocumento() == 99) {
            return "Consumidor final";
        }
        if (comprobante.receptorCuitCuil() != null && !comprobante.receptorCuitCuil().isBlank()) {
            return "CUIT/CUIL: " + formatearCuit(comprobante.receptorCuitCuil());
        }
        return "Tipo " + comprobante.receptorTipoDocumento()
                + ": " + comprobante.receptorNumeroDocumento();
    }

    private String formatearCuit(String cuit) {
        String digits = cuit == null ? "" : cuit.replaceAll("\\D", "");
        if (digits.length() != 11) {
            return valorOGuion(cuit);
        }
        return digits.substring(0, 2) + "-" + digits.substring(2, 10) + "-" + digits.substring(10);
    }

    private String formatAmount(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$ " + format.format(amount);
    }

    private String formatNumber(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private String valorOGuion(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
