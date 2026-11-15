package com.example.facturacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConfigurationProperties(prefix = "facturacion.comprobante.emisor")
public class EmisorComprobanteProperties {

    private String denominacion;
    private String cuit;
    private String condicionIva;
    private String domicilio;

    public boolean tieneDatosCompletos() {
        return denominacion != null && !denominacion.isBlank()
                && tieneCuitValido()
                && condicionIva != null && !condicionIva.isBlank()
                && domicilio != null && !domicilio.isBlank();
    }

    public void validar() {
        if (!tieneDatosCompletos()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure los datos fiscales del emisor para generar el comprobante PDF");
        }
    }

    public long obtenerCuitNumerico() {
        if (!tieneCuitValido()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure un CUIT válido para solicitar autorización a ARCA");
        }
        return Long.parseLong(cuit.replaceAll("\\D", ""));
    }

    private boolean tieneCuitValido() {
        return cuit != null && cuit.replaceAll("\\D", "").matches("\\d{11}");
    }

    public String getDenominacion() {
        return denominacion;
    }

    public void setDenominacion(String denominacion) {
        this.denominacion = denominacion;
    }

    public String getCuit() {
        return cuit;
    }

    public void setCuit(String cuit) {
        this.cuit = cuit;
    }

    public String getCondicionIva() {
        return condicionIva;
    }

    public void setCondicionIva(String condicionIva) {
        this.condicionIva = condicionIva;
    }

    public String getDomicilio() {
        return domicilio;
    }

    public void setDomicilio(String domicilio) {
        this.domicilio = domicilio;
    }
}
