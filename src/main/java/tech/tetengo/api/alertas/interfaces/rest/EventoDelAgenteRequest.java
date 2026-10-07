package tech.tetengo.api.alertas.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Detected event of AGENT_CONTRACT.md. */
record EventoDelAgenteRequest(
        @NotNull(message = "Falta el identificador del evento.")
        UUID eventoId,

        @NotNull(message = "Falta el tipo de evento.")
        @Pattern(
                regexp = "caida|caida_confirmada|movimiento_inestable|recuperacion|deteccion_no_confiable",
                message = "Tipo de evento desconocido.")
        String tipo,

        @NotNull(message = "Falta la hora del evento.") Instant ocurridoEn,
        Map<String, Object> parametros) {}
