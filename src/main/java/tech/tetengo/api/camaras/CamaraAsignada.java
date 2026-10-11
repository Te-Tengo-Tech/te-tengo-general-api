package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/**
 * US-29 / CA-29.2: a camera started watching a room, or moved to another one.
 *
 * @param ambienteAnterior the room it watched before; null when it starts being used
 * @param usuarioId the member who changed it in the app; null when it was done on the PC
 * @param pcId the camera's PC (the PC that made the change when {@code usuarioId} is null)
 */
public record CamaraAsignada(
        UUID hogarId,
        UUID camaraId,
        UUID pcId,
        String nombreDispositivo,
        String ambienteAnterior,
        String ambienteNuevo,
        UUID usuarioId,
        Instant ocurridaEn) {}
