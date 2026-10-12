package tech.tetengo.api.hogares;

import java.time.Instant;
import java.util.UUID;

/**
 * US-05 / CA-05.1: the older adult's consent was registered, so camera capture may start.
 *
 * @param usuarioId the owner who registered it (US-33 activity log); null in events older than it
 * @param otorgadoPor who gave it
 */
public record ConsentimientoOtorgado(UUID hogarId, Instant otorgadoEn, UUID usuarioId, String otorgadoPor) {

    public ConsentimientoOtorgado(UUID hogarId, Instant otorgadoEn) {
        this(hogarId, otorgadoEn, null, null);
    }
}
