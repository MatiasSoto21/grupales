package com.example.facturacion.controllers;

import com.example.facturacion.dto.ClienteRequestDTO;
import com.example.facturacion.dto.ClienteResponseDTO;
import com.example.facturacion.services.ClienteService;
import lombok.RequiredArgsConstructor;
import com.example.facturacion.dto.ClienteBusquedaDTO;
import com.example.facturacion.dto.PageResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteRestController {

    private final ClienteService clienteService;

    @GetMapping
    public PageResponseDTO<ClienteBusquedaDTO> buscar(
            @RequestParam(required = false) String denominacion,
            @RequestParam(required = false) String cuitCuil,
            @RequestParam(defaultValue = "parcial") String coincidencia,
            @RequestParam(required = false) Long condicionIvaId,
            @RequestParam(defaultValue = "denominacion") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return clienteService.buscarClientes(denominacion, cuitCuil, coincidencia, condicionIvaId, sortBy, sortDir, page, size);
    }

    @GetMapping("/{id}")
    public ClienteBusquedaDTO obtener(
            @PathVariable Long id) {
        return clienteService.obtenerCliente(id);
    }

    @PostMapping
    public ResponseEntity<ClienteResponseDTO> crear(@RequestBody ClienteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crearCliente(request));
    }
}
