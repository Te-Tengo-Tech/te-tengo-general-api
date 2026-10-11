package tech.tetengo.api.hogares;

import java.time.Instant;
import java.util.UUID;

/**
 * US-09 / CA-09.1: the consent was revoked: capture stops and every recording is to be deleted.
 *
 * @param usuarioId the owner who revoked it (US-33 activity log); null in events older than it
 */
public record ConsentimientoRevocado(UUID hogarId, Instant revocadoEn, UUID usuarioId) {

    public ConsentimientoRevocado(UUID hogarId, Instant revocadoEn) {
        this(hogarId, revocadoEn, null);
    }
}
