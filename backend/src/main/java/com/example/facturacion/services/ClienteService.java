package com.example.facturacion.services;

import com.example.facturacion.dto.ClienteRequestDTO;
import com.example.facturacion.dto.ClienteResponseDTO;
import com.example.facturacion.entities.Cliente;
import com.example.facturacion.entities.CondicionIva;
import com.example.facturacion.entities.Contacto;
import com.example.facturacion.entities.Domicilio;
import com.example.facturacion.entities.Usuario;
import com.example.facturacion.repositories.ClienteRepository;
import com.example.facturacion.repositories.DomicilioRepository;
import com.example.facturacion.repositories.ContactoRepository;
import com.example.facturacion.repositories.BloqueoTransaccionalRepository;
import com.example.facturacion.repositories.CondicionIvaRepository;
import com.example.facturacion.repositories.UsuarioRepository;
import java.time.LocalDate;
import com.example.facturacion.dto.ClienteBusquedaDTO;
import com.example.facturacion.dto.PageResponseDTO;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CondicionIvaRepository condicionIvaRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private RequestValidation requestValidation;

    @org.springframework.beans.factory.annotation.Autowired
    private BloqueoTransaccionalRepository bloqueos;
    @org.springframework.beans.factory.annotation.Autowired
    private ContactoRepository contactoRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private DomicilioRepository domicilioRepository;

    public ClienteService(
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            CondicionIvaRepository condicionIvaRepository) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.condicionIvaRepository = condicionIvaRepository;
    }

    @Transactional(readOnly = true)
    public ClienteBusquedaDTO obtenerCliente(Long id) {
        return ClienteBusquedaDTO.from(clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente indicado")));
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<ClienteBusquedaDTO> buscarClientes(
            String denominacion, String cuitCuil, String coincidencia, Long condicionIvaId,
            String sortBy, String sortDir, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / Math.max(size, 1)
                || !Set.of("denominacion", "cuitCuil").contains(sortBy)
                || !Set.of("asc", "desc").contains(sortDir)
                || !Set.of("parcial", "exacta").contains(coincidencia)
                || (condicionIvaId != null && condicionIvaId < 1)
                || (denominacion != null && denominacion.length() > 200)
                || (cuitCuil != null && (cuitCuil.length() > 30 || !cuitCuil.matches("[0-9 -]*")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtros de clientes invalidos");
        }
        var result = clienteRepository.buscarFiltrados(denominacion, cuitCuil, coincidencia, condicionIvaId,
                sortBy, sortDir, page, size);
        return new PageResponseDTO<>(result.getContent().stream()
                .map(ClienteBusquedaDTO::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }



    @Transactional
    public ClienteResponseDTO crearCliente(ClienteRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos del cliente son obligatorios");
        }

        requestValidation.validate(request);

        String cuitCuil = trim(request.getCuitCuil());
        String denominacion = trim(request.getDenominacion());
        if (cuitCuil == null || denominacion == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CUIT/CUIL y denominación son obligatorios");
        }
        if (request.getCondicionIvaId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La condición de IVA es obligatoria");
        }
        if (!cuitCuil.matches("[0-9 -]+") || cuitCuil.replaceAll("[^0-9]", "").length() != 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CUIT/CUIL debe contener 11 digitos");
        }
        cuitCuil = cuitCuil.replaceAll("[^0-9]", "");
        // Serialize concurrent creations of the same CUIT until transaction commit.
        bloqueos.bloquearCuit(Long.parseLong(cuitCuil));
        if (clienteRepository.existsByCuitNormalizado(cuitCuil)) {
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
        contactoRepository.save(contacto);

        Domicilio domicilio = new Domicilio();
        domicilio.setNombreCalle(trim(request.getNombreCalle()));
        domicilio.setNumeroCalle(trim(request.getNumeroCalle()));
        domicilioRepository.save(domicilio);

        Cliente cliente = new Cliente();
        cliente.setCuitCuil(cuitCuil);
        cliente.setDenominacion(denominacion);
        if (request.getCondicionIvaId() != null) {
            CondicionIva condicionIva = condicionIvaRepository.findById(request.getCondicionIvaId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "No existe la condición de IVA indicada"));
            cliente.setCondicionIva(condicionIva);
        }
        cliente.setContacto(contacto);
        cliente.setDomicilio(domicilio);
        cliente.setFechaAlta(hoy);
        cliente.setFechaModificacion(hoy);
        cliente.setUsuarioCarga(usuario);
        cliente.setUsuarioModificacion(usuario);

        Cliente guardado = clienteRepository.save(cliente);
        return new ClienteResponseDTO(
                guardado.getId(),
                guardado.getDenominacion(),
                guardado.getCondicionIva() == null ? null : guardado.getCondicionIva().getId());
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
