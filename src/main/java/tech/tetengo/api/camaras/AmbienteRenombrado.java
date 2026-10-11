package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/** US-29: the owner renamed a room; its cameras and their later alerts use the new name. */
public record AmbienteRenombrado(
        UUID hogarId, UUID ambienteId, String nombreAnterior, String nombreNuevo, UUID usuarioId, Instant ocurridaEn) {}
