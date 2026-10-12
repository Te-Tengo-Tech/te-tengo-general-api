package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/** The owner renamed a PC in the app. */
public record PcRenombrada(
        UUID hogarId, UUID pcId, String nombreAnterior, String nombreNuevo, UUID usuarioId, Instant ocurridaEn) {}
