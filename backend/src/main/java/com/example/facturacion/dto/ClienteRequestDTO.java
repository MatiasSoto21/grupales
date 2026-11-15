package com.example.facturacion.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClienteRequestDTO {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 20)
    private String cuitCuil;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 200)
    private String denominacion;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Long condicionIvaId;
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max = 200)
    private String email;
    @jakarta.validation.constraints.Size(max = 50)
    private String telefono;
    @jakarta.validation.constraints.Size(max = 50)
    private String celular;
    @jakarta.validation.constraints.Size(max = 200)
    private String nombreCalle;
    @jakarta.validation.constraints.Size(max = 20)
    private String numeroCalle;
}
