package tech.tetengo.api.movimientos.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CA-35.2: who gives the consent (the older adult in person, or their representative and their name),
 * that they accepted, and the version of the text shown.
 */
record ConsentimientoDeMovimientosRequest(
        @NotBlank(message = "Escribe quién da el permiso.") @Size(max = 120)
        String otorgadoPor,

        @NotNull(message = "Indica si lo da en persona o su representante.")
        Boolean representante,

        Boolean aceptado,

        @NotBlank(message = "Falta la versión del texto aceptado.") @Size(max = 20)
        String versionTexto) {}
