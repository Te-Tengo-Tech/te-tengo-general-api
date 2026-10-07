package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import tech.tetengo.api.camaras.application.MotivoSinCaptura;

/** {@code EstadoCaptura} of AGENT_CONTRACT.md. */
record EstadoDeCapturaResponse(
        boolean capturaPermitida, MotivoSinCaptura motivo, Instant pausadaHasta, String nombreHabitacion) {}
