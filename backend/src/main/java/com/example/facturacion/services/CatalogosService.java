package com.example.facturacion.services;

import com.example.facturacion.entities.*;
import com.example.facturacion.repositories.CatalogosRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Service
public class CatalogosService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.facturacion.services.ReceptorArcaPolicy receptorArcaPolicy;

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.facturacion.services.FechaFacturacionPolicy fechas;

    @org.springframework.beans.factory.annotation.Autowired
    private CatalogosRepository catalogos;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> obtenerCatalogos() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("facturacion", Map.of("ambiente", receptorArcaPolicy.getAmbiente(), "fechaActual", fechas.hoy().toString(),
                "fechaFiscalMinima", fechas.fechaFiscalMinima().toString(), "fechaFiscalMaxima", fechas.hoy().toString()));

        var estados = new java.util.LinkedHashSet<>(List.of("EMITIDA", "AUTORIZADA", "RECHAZADA", "ERROR_AFIP", "ANULADA"));
        // Legacy payment labels are filterable only if real historical records still use them.
        estados.addAll(catalogos.estadosHistoricos());
        resultado.put("estadosFactura", new ArrayList<>(estados));

        List<Map<String, Object>> condiciones = new ArrayList<>();
        for (CondicionIva c : catalogos.condiciones()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("denominacion", c.getDenominacion());
            item.put("codigoAfip", c.getCodigoAfip());
            condiciones.add(item);
        }

        List<Map<String, Object>> monedas = new ArrayList<>();
        for (TipoMoneda m : catalogos.monedas()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("denominacion", m.getDenominacion());
            item.put("simbolo", m.getSimbolo());
            monedas.add(item);
        }

        List<Map<String, Object>> puntos = new ArrayList<>();
        for (PuntoVenta p : catalogos.puntos()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("numero", p.getNumero());
            item.put("descripcion", p.getDescripcion());
            puntos.add(item);
        }

        resultado.put("listasPrecio", catalogos.listas().stream().map(l -> Map.of("id", l.getId(), "codigo", l.getCodigo(), "denominacion", l.getDenominacion())).toList());
        resultado.put("clientes", List.of());
        resultado.put("condicionesIva", condiciones);
        resultado.put("tiposMoneda", monedas);
        resultado.put("puntosVenta", puntos);
        resultado.put("articulos", List.of());
        return resultado;
    }
}