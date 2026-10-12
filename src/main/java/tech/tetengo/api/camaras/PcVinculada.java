package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/** US-28 / CA-28.1: the owner linked a PC to the household from the app. */
public record PcVinculada(UUID hogarId, UUID pcId, String nombre, UUID usuarioId, Instant ocurridaEn) {}
