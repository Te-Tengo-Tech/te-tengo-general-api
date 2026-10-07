package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** CA-04.3: every field is required, so the app can highlight the missing one. */
record AdultoMayorRequest(
        @NotBlank(message = "Ingresa el nombre del adulto mayor.")
        @Size(max = 120, message = "El nombre es demasiado largo.")
        String nombre,

        @NotBlank(message = "Ingresa la dirección de la vivienda.")
        @Size(max = 250, message = "La dirección es demasiado larga.")
        String direccion,

        @NotBlank(message = "Indica con quién vive el adulto mayor.")
        @Pattern(regexp = "SOLO|CON_FAMILIAR|CON_CUIDADOR", message = "Elige una opción de convivencia válida.")
        String convivencia) {}
