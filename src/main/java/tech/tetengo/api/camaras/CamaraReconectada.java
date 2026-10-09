package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/** US-07 / CA-07.3: a disconnected camera is sending heartbeats again. */
public record CamaraReconectada(UUID hogarId, UUID camaraId, String habitacion, Instant ocurridaEn) {}
