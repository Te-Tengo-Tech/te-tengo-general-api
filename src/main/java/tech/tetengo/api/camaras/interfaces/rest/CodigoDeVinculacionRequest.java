package tech.tetengo.api.camaras.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * CA-28.2: the PC's code as scanned (the {@code codigo} parameter of the QR) or typed: 8 letters of
 * {@code BCDFGHJKLMNPQRSTVWXZ}, in any case, with or without the hyphen.
 */
record CodigoDeVinculacionRequest(
        @NotBlank(message = "Escribe el código de la PC.")
        @Pattern(
                regexp = "(?i)\\s*[BCDFGHJKLMNPQRSTVWXZ]{4}[\\s-]?[BCDFGHJKLMNPQRSTVWXZ]{4}\\s*",
                message = "El código tiene 8 letras, solo consonantes y sin números.")
        String codigo) {}
