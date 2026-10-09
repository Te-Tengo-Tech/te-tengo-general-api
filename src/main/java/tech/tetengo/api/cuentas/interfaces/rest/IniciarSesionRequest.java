package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

record IniciarSesionRequest(
        @NotBlank(message = "Ingresa tu correo.") String correo,
        @NotBlank(message = "Ingresa tu contraseña.") String contrasena) {}
