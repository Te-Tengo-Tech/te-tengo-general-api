package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

record RefrescarSesionRequest(
        @NotBlank(message = "Falta el token de refresco.") String tokenRefresco) {}
