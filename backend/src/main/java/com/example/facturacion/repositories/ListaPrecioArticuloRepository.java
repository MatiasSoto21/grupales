package com.example.facturacion.repositories;

import com.example.facturacion.entities.ListaPrecioArticulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;

import java.util.Optional;

@Repository
public interface ListaPrecioArticuloRepository
        extends JpaRepository<ListaPrecioArticulo, Long>, JpaSpecificationExecutor<ListaPrecioArticulo> {

    Map<String, String> SORT_FIELDS = Map.of(
            "denominacion", "articulo.denominacion", "codigo", "articulo.numeroCatalogo", "precioVenta", "precioVenta");

        Optional<ListaPrecioArticulo> findByArticulo_IdAndListaPrecio_Id(Long articuloId, Long listaPrecioId);
    default Page<ListaPrecioArticulo> buscarFiltrados(Long listaPrecioId,
            String denominacion, String codigo, String coincidencia, String sortBy, String sortDir, int page, int size) {
        Specification<ListaPrecioArticulo> filters = (precio, query, cb) -> {
            var conditions = new ArrayList<Predicate>();
            conditions.add(cb.equal(precio.get("listaPrecio").get("id"), listaPrecioId));
            conditions.add(cb.gt(precio.get("precioVenta"), 0));
            conditions.add(cb.le(precio.get("precioVenta"), Double.MAX_VALUE));
            for (var entry : Map.of("denominacion", denominacion == null ? "" : denominacion,
                    "codigo", codigo == null ? "" : codigo).entrySet()) {
                String value = entry.getValue().trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                if ("codigo".equals(entry.getKey()) && value.matches("[0-9]+")) {
                    var numero = precio.get("articulo").get("numeroCatalogo").as(String.class);
                    conditions.add("exacta".equals(coincidencia) ? cb.equal(numero, value) : cb.like(numero, "%" + value + "%"));
                    continue;
                }
                var field = cb.lower(precio.get("articulo").<String>get(entry.getKey()));
                conditions.add("exacta".equals(coincidencia) ? cb.equal(field, value)
                        : cb.like(field, "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", '\\'));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        var sort = Sort.by(Sort.Direction.fromString(sortDir), SORT_FIELDS.get(sortBy)).and(Sort.by("id"));
        return findAll(filters, PageRequest.of(page, size, sort));
    }
}
