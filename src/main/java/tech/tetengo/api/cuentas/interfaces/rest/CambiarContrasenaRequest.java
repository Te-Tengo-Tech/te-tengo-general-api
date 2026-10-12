package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API contract §13: the new password has 8 characters or more, with a number. */
record CambiarContrasenaRequest(
        @NotBlank(message = "Ingresa tu contraseña actual.") @Size(max = 72)
        String actual,

        @NotBlank(message = "Ingresa una contraseña nueva.")
        @Size(min = 8, max = 72, message = "Usa 8 caracteres o más, con al menos un número.")
        @Pattern(regexp = ".*[0-9].*", message = "Usa 8 caracteres o más, con al menos un número.")
        String nueva) {}
