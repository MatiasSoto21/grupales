package com.example.facturacion.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClienteRequestDTO {
    private String cuitCuil;
    private String denominacion;
    private String email;
    private String telefono;
    private String celular;
    private String nombreCalle;
    private String numeroCalle;
}
