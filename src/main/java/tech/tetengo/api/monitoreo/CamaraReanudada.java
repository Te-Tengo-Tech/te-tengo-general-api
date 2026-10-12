package tech.tetengo.api.monitoreo;

import java.time.Instant;
import java.util.UUID;

/** US-22: a member resumed a paused camera before its end (US-33 activity log). */
public record CamaraReanudada(UUID hogarId, UUID camaraId, String habitacion, UUID usuarioId, Instant ocurridaEn) {}
