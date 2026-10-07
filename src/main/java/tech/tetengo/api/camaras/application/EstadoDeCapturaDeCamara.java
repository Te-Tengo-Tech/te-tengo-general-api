package tech.tetengo.api.camaras.application;

import java.time.Instant;

/**
 * What the agent needs to decide whether to process video (CA-05.2, CA-22.1).
 *
 * @param motivo why capture is not allowed, or {@code null} when it is
 * @param pausadaHasta end of the active pause, or {@code null}
 * @param nombreHabitacion the room name stored in the backend (CA-06.2)
 */
public record EstadoDeCapturaDeCamara(
        boolean capturaPermitida, MotivoSinCaptura motivo, Instant pausadaHasta, String nombreHabitacion) {}
