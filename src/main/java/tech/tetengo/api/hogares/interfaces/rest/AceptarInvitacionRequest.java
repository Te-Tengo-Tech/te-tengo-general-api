package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only for a new account; an existing account accepts with its bearer token and no body. */
record AceptarInvitacionRequest(
        @NotBlank(message = "Ingresa tu nombre.") @Size(max = 120, message = "El nombre es demasiado largo.")
        String nombre,

        @NotBlank(message = "Ingresa una contraseña.") @Size(max = 72, message = "La contraseña es demasiado larga.")
        String contrasena) {}
