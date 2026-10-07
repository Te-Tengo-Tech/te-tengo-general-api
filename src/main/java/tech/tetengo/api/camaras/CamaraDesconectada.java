package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/** US-07 / CA-07.2: the camera stopped sending heartbeats. */
public record CamaraDesconectada(UUID hogarId, UUID camaraId, String habitacion, Instant ocurridaEn) {}
