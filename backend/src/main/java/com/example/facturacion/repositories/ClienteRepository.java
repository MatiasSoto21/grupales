
package com.example.facturacion.repositories;

import com.example.facturacion.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;
import java.util.ArrayList;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface ClienteRepository
        extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {
    boolean existsByCuitCuilIgnoreCase(String cuitCuil);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM ventas.cliente "
            + "WHERE regexp_replace(cuit_cuil, '[^0-9]', '', 'g') = :cuit", nativeQuery = true)
    boolean existsByCuitNormalizado(@org.springframework.data.repository.query.Param("cuit") String cuit);
    default Page<Cliente> buscarFiltrados(String denominacion, String cuitCuil,
            String coincidencia, Long condicionIvaId, String sortBy, String sortDir, int page, int size) {
        String nombre = denominacion == null || denominacion.isBlank() ? null : denominacion.trim();
        String documento = cuitCuil == null ? null : cuitCuil.replaceAll("[^0-9]", "");
        Specification<Cliente> filtros = (cliente, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (nombre != null) {
                var field = cb.lower(cliente.<String>get("denominacion"));
                String value = nombre.toLowerCase(Locale.ROOT);
                predicates.add("exacta".equals(coincidencia) ? cb.equal(field, value)
                        : cb.like(field, "%" + escaparLike(value) + "%", '\\'));
            }
            if (documento != null && !documento.isEmpty()) {
                var field = cb.function("replace", String.class,
                        cb.function("replace", String.class, cliente.get("cuitCuil"), cb.literal("-"), cb.literal("")),
                        cb.literal(" "), cb.literal(""));
                predicates.add("exacta".equals(coincidencia) ? cb.equal(field, documento)
                        : cb.like(field, "%" + documento + "%"));
            }
            if (condicionIvaId != null) predicates.add(cb.equal(cliente.get("condicionIva").get("id"), condicionIvaId));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var direction = Sort.Direction.fromString(sortDir);
        var sort = Sort.by(direction, sortBy).and(Sort.by("id"));
        return findAll(filtros, PageRequest.of(page, size, sort));
    }

    private static String escaparLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
}
