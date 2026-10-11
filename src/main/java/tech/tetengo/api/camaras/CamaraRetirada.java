package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/**
 * US-29: «Dejar de usar»: the camera no longer watches its room.
 *
 * @param usuarioId the owner who did it from the app; null when it was done on the PC (CA-29.8)
 */
public record CamaraRetirada(
        UUID hogarId,
        UUID camaraId,
        UUID pcId,
        String nombreDispositivo,
        String ambiente,
        Instant ocurridaEn,
        UUID usuarioId) {

    public CamaraRetirada(
            UUID hogarId, UUID camaraId, UUID pcId, String nombreDispositivo, String ambiente, Instant ocurridaEn) {
        this(hogarId, camaraId, pcId, nombreDispositivo, ambiente, ocurridaEn, null);
    }
}
