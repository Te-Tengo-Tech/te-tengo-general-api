package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

record SolicitarRecuperacionRequest(
        @NotBlank(message = "Ingresa tu correo.") String correo) {}
