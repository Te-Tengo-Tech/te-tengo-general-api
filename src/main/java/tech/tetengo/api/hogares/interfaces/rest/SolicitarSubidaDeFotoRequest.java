package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * API contract §13. An unknown {@code tipo} answers {@code 415 FOTO_FORMATO_NO_ADMITIDO} and a size over
 * 5 MB {@code 413 FOTO_MUY_GRANDE}, so neither is a validation error.
 */
record SolicitarSubidaDeFotoRequest(
        @NotBlank(message = "Indica de quién es la foto.")
        @Pattern(regexp = "YO|ADULTO_MAYOR", message = "Indica de quién es la foto.")
        String de,

        @NotBlank(message = "Indica el tipo de la foto.") String tipo,

        @NotNull(message = "Indica el tamaño de la foto.") @Positive(message = "Indica el tamaño de la foto.")
        Long tamanoBytes) {}
