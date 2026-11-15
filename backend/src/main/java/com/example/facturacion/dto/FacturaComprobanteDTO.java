package com.example.facturacion.dto;

import java.time.LocalDate;
import java.util.List;

public record FacturaComprobanteDTO(
        Long id,
        String ambiente,
        String emisorDenominacion,
        String emisorCuit,
        String emisorCondicionIva,
        String emisorDomicilio,
        String receptorDenominacion,
        String receptorCuitCuil,
        String receptorDomicilio,
        int receptorTipoDocumento,
        long receptorNumeroDocumento,
        String receptorCondicionIva,
        int puntoVentaNumero,
        String puntoVentaDomicilio,
        int tipoComprobante,
        Long numero,
        LocalDate fechaEmision,
        List<Detalle> detalles,
        double importeNeto,
        double importeIva,
        double importeTotal,
        String moneda,
        double cotizacionMoneda,
        String cae,
        LocalDate caeFechaVencimiento,
        String estadoSistema,
        boolean receptorPrueba) {

    public record Detalle(
            String descripcion,
            double cantidad,
            double precioUnitario,
            double porcentajeBonificacion,
            double importeNeto,
            double importeIva,
            double importeSubtotal) {
    }
}
