package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** CA-01.3: every field is required. BCrypt only uses the first 72 bytes of a password. */
record RegistrarCuentaRequest(
        @NotBlank(message = "Ingresa tu correo.") @Email(message = "Ingresa un correo válido.") @Size(max = 254)
        String correo,

        @NotBlank(message = "Ingresa una contraseña.") @Size(max = 72, message = "La contraseña es demasiado larga.")
        String contrasena,

        @NotBlank(message = "Ingresa tu nombre.") @Size(max = 120, message = "El nombre es demasiado largo.")
        String nombre) {}
