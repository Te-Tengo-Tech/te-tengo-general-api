package tech.tetengo.api.shared.application.port;

import java.util.UUID;

/**
 * Implemented by {@code hogares}: whether a user still belongs to a household. A member whose access
 * was removed (CA-08.3) keeps a valid token for a while, so the household of the token is only bound
 * after this check.
 */
public interface ComprobadorDeMembresia {

    boolean esMiembro(UUID hogarId, UUID usuarioId);
}
