package tech.tetengo.api.camaras.application;

import java.time.Instant;

/** What the agent needs to decide whether to process video (CA-05.2, CA-22.1). */
public record EstadoDeCapturaDeCamara(boolean capturaPermitida, boolean consentimientoVigente, Instant pausadaHasta) {}
