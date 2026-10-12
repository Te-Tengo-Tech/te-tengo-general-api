package tech.tetengo.api.monitoreo;

import java.time.Instant;
import java.util.UUID;

/** US-22 / CA-22.1: a member paused a camera until {@code hasta} (US-33 activity log). */
public record CamaraPausada(
        UUID hogarId, UUID camaraId, String habitacion, Instant hasta, UUID usuarioId, Instant ocurridaEn) {}
