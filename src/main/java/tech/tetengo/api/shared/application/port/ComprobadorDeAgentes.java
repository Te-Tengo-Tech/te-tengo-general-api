package tech.tetengo.api.shared.application.port;

import java.util.UUID;

/**
 * Implemented by {@code camaras}: whether a household agent token still works. A PC that was unlinked
 * (US-31), or a camera that was retired, keeps a signed token for a while, so the household of an agent
 * token is only bound after this check, and the request is rejected with {@code 401 PC_DESVINCULADA}
 * otherwise.
 */
public interface ComprobadorDeAgentes {

    /**
     * @param pcId the {@code pc_id} claim, null for the tokens of installation credentials
     * @param camaraId the {@code camara_id} claim, null for a PC's own token
     * @return false when the PC is not linked to the household any more, or the camera was retired or
     *     belongs to another PC. An installation token of a camera that does not exist is left to the
     *     endpoint ({@code 404}), as before PCs existed
     */
    boolean autorizado(UUID hogarId, UUID pcId, UUID camaraId);
}
