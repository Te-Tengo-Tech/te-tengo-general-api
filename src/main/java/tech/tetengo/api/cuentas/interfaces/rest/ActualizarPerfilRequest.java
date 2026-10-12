package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API contract §13: the phone follows the older adult's rule; blank phone or relationship clears it. */
record ActualizarPerfilRequest(
        @NotBlank(message = "Ingresa tu nombre.") @Size(max = 80, message = "El nombre es demasiado largo.")
        String nombre,

        @Pattern(regexp = "\\s*|\\+?[0-9 ]{6,20}", message = "Escribe un número de teléfono válido.")
        String telefono,

        @Size(max = 40, message = "El parentesco es demasiado largo.")
        String parentesco) {}
