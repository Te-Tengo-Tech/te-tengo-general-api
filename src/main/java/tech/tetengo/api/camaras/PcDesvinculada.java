package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * US-31: a PC left the household. Its tokens no longer work and its cameras were retired; the PC
 * learns it on its control channel at once, or on its next request.
 *
 * @param motivo {@code DESDE_LA_APP}, {@code DESDE_LA_PC}, {@code REUSO_DE_TOKEN} or {@code VENCIDA}
 * @param usuarioId the owner who removed it from the app; null otherwise
 * @param camaras the PC's cameras, now retired
 * @param habitaciones the rooms that lose detection (CA-31.4)
 */
public record PcDesvinculada(
        UUID hogarId,
        UUID pcId,
        String nombre,
        String motivo,
        UUID usuarioId,
        List<UUID> camaras,
        List<String> habitaciones,
        Instant ocurridaEn) {

    public static final String DESDE_LA_APP = "DESDE_LA_APP";

    /** CA-31.3: the family is told when the PC left without the app (from the PC, or for security). */
    public boolean avisarALaFamilia() {
        return !DESDE_LA_APP.equals(motivo);
    }
}
