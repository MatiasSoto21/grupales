package com.example.facturacion.controllers;

import com.example.facturacion.entities.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogosRestController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public Map<String, Object> obtenerCatalogos() {
        Map<String, Object> resultado = new LinkedHashMap<>();

        List<Map<String, Object>> clientes = new ArrayList<>();
        for (Cliente c : em.createQuery("SELECT c FROM Cliente c ORDER BY c.denominacion", Cliente.class).getResultList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("denominacion", c.getDenominacion());
            clientes.add(item);
        }

        List<Map<String, Object>> condiciones = new ArrayList<>();
        for (CondicionIva c : em.createQuery("SELECT c FROM CondicionIva c ORDER BY c.denominacion", CondicionIva.class).getResultList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("denominacion", c.getDenominacion());
            condiciones.add(item);
        }

        List<Map<String, Object>> monedas = new ArrayList<>();
        for (TipoMoneda m : em.createQuery("SELECT m FROM TipoMoneda m ORDER BY m.denominacion", TipoMoneda.class).getResultList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("denominacion", m.getDenominacion());
            item.put("simbolo", m.getSimbolo());
            monedas.add(item);
        }

        List<Map<String, Object>> puntos = new ArrayList<>();
        for (PuntoVenta p : em.createQuery("SELECT p FROM PuntoVenta p ORDER BY p.numero", PuntoVenta.class).getResultList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("numero", p.getNumero());
            item.put("descripcion", p.getDescripcion());
            puntos.add(item);
        }

        List<ListaPrecioArticulo> precios = em.createQuery(
                "SELECT l FROM ListaPrecioArticulo l JOIN FETCH l.articulo JOIN FETCH l.listaPrecio ORDER BY l.articulo.denominacion, l.listaPrecio.denominacion, l.id",
                ListaPrecioArticulo.class
        ).getResultList();
        Map<Long, Map<String, Object>> articulosUnicos = new LinkedHashMap<>();
        for (ListaPrecioArticulo l : precios) {
            Long articuloId = l.getArticulo().getId();
            if (!articulosUnicos.containsKey(articuloId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", articuloId);
                item.put("codigo", l.getArticulo().getCodigo());
                item.put("denominacion", l.getArticulo().getDenominacion());
                item.put("precios", new ArrayList<Map<String, Object>>());
                articulosUnicos.put(articuloId, item);
            }

            Map<String, Object> precio = new LinkedHashMap<>();
            precio.put("id", l.getId());
            precio.put("listaPrecioId", l.getListaPrecio().getId());
            precio.put("listaPrecioDenominacion", l.getListaPrecio().getDenominacion());
            precio.put("precioVenta", l.getPrecioVenta());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> preciosArticulo =
                    (List<Map<String, Object>>) articulosUnicos.get(articuloId).get("precios");
            preciosArticulo.add(precio);
        }

        resultado.put("clientes", clientes);
        resultado.put("condicionesIva", condiciones);
        resultado.put("tiposMoneda", monedas);
        resultado.put("puntosVenta", puntos);
        resultado.put("articulos", new ArrayList<>(articulosUnicos.values()));
        return resultado;
    }
}