package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ConfirmarRecuperacionRequest(
        @NotBlank(message = "Falta el token del enlace.") String token,

        @NotBlank(message = "Ingresa una contraseña.") @Size(max = 72, message = "La contraseña es demasiado larga.")
        String nuevaContrasena) {}
