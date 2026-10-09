package tech.tetengo.api.alertas.interfaces.rest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Optional body of the clip upload request (AGENT_CONTRACT.md). {@code contentType} defaults to
 * {@code video/mp4}; {@code tamanoBytes} is informational.
 */
record SolicitudDeClipRequest(
        @Pattern(regexp = "video/[A-Za-z0-9.+-]+", message = "El tipo de contenido debe ser un video.")
        String contentType,

        @Positive(message = "El tamaño debe ser mayor que cero.")
        Long tamanoBytes) {}
