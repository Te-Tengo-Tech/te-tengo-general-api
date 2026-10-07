package tech.tetengo.api.alertas;

import java.time.Instant;

/** Public API of {@code alertas} for the retention policy of {@code historial} (US-26). */
public interface RetencionDeClips {

    /**
     * Deletes, in every household, the clips of alerts that happened before {@code limite}; their
     * alerts then answer {@code 410 CLIP_ELIMINADO} (CA-26.3). Returns how many were deleted.
     */
    int eliminarAnterioresA(Instant limite);
}
