package tech.tetengo.api.camaras;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of {@code camaras} for the other modules. Works on the household of the current
 * request or {@code EjecutorEnHogar} call.
 */
public interface CamarasDelHogar {

    Optional<CamaraDelHogar> buscar(UUID camaraId);

    /** CA-15.3: only frames that were discarded for 5 minutes. True if it was reliable until now. */
    boolean marcarDeteccionNoConfiable(UUID camaraId);

    /** The agent sees the person again. */
    void marcarDeteccionConfiable(UUID camaraId);

    /**
     * @param capturaPermitida a current consent and no active pause (CA-05.2, CA-22.1)
     */
    record CamaraDelHogar(UUID id, String nombreHabitacion, boolean capturaPermitida) {}
}
