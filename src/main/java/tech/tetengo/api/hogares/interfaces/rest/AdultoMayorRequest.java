package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CA-04.3: every field but the phone is required, so the app can highlight the missing one. The age
 * range and its messages come from the prototype's profile form.
 */
record AdultoMayorRequest(
        @NotBlank(message = "Ingresa el nombre del adulto mayor.")
        @Size(max = 120, message = "El nombre es demasiado largo.")
        String nombre,

        @NotNull(message = "Escribe su edad.")
        @Min(value = EDAD_MINIMA, message = "Escribe una edad válida, en años.")
        @Max(value = EDAD_MAXIMA, message = "Escribe una edad válida, en años.")
        Integer edad,

        @NotBlank(message = "Ingresa la dirección de la vivienda.")
        @Size(max = 250, message = "La dirección es demasiado larga.")
        String direccion,

        @NotBlank(message = "Indica con quién vive el adulto mayor.")
        @Pattern(regexp = "SOLO|CON_FAMILIAR|CON_CUIDADOR", message = "Elige una opción de convivencia válida.")
        String convivencia,

        @Pattern(regexp = "\\s*|\\+?[0-9 ]{6,20}", message = "Escribe un número de teléfono válido.")
        String telefono) {

    static final int EDAD_MINIMA = 50;
    static final int EDAD_MAXIMA = 120;
}
