package com.example.facturacion.services;

import com.example.facturacion.dto.ClienteRequestDTO;
import com.example.facturacion.dto.ClienteResponseDTO;
import com.example.facturacion.entities.Cliente;
import com.example.facturacion.entities.Contacto;
import com.example.facturacion.entities.Domicilio;
import com.example.facturacion.entities.Usuario;
import com.example.facturacion.repositories.ClienteRepository;
import com.example.facturacion.repositories.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ClienteService(ClienteRepository clienteRepository, UsuarioRepository usuarioRepository) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ClienteResponseDTO crearCliente(ClienteRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos del cliente son obligatorios");
        }

        String cuitCuil = trim(request.getCuitCuil());
        String denominacion = trim(request.getDenominacion());
        if (cuitCuil == null || denominacion == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CUIT/CUIL y denominación son obligatorios");
        }
        if (clienteRepository.existsByCuitCuilIgnoreCase(cuitCuil)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un cliente con ese CUIT/CUIL");
        }

        Usuario usuario = usuarioRepository.findByUsuario("admin")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario admin para la auditoría"));
        LocalDate hoy = LocalDate.now();

        Contacto contacto = new Contacto();
        contacto.setEmail(trim(request.getEmail()));
        contacto.setTelefono(trim(request.getTelefono()));
        contacto.setCelular(trim(request.getCelular()));
        entityManager.persist(contacto);

        Domicilio domicilio = new Domicilio();
        domicilio.setNombreCalle(trim(request.getNombreCalle()));
        domicilio.setNumeroCalle(trim(request.getNumeroCalle()));
        entityManager.persist(domicilio);

        Cliente cliente = new Cliente();
        cliente.setCuitCuil(cuitCuil);
        cliente.setDenominacion(denominacion);
        cliente.setContacto(contacto);
        cliente.setDomicilio(domicilio);
        cliente.setFechaAlta(hoy);
        cliente.setFechaModificacion(hoy);
        cliente.setUsuarioCarga(usuario);
        cliente.setUsuarioModificacion(usuario);

        Cliente guardado = clienteRepository.save(cliente);
        return new ClienteResponseDTO(guardado.getId(), guardado.getDenominacion());
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
