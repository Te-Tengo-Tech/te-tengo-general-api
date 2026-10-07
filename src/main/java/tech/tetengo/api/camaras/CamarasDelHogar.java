package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of {@code camaras} for the other modules. Works on the household of the current
 * request or {@code EjecutorEnHogar} call.
 */
public interface CamarasDelHogar {

    Optional<CamaraDelHogar> buscar(UUID camaraId);

    /** The camera as the app sees it. */
    Optional<EstadoDeCamara> estado(UUID camaraId);

    /**
     * CA-15.3: only frames that were discarded for 5 minutes, reported at {@code desde}. True if it
     * was reliable until now.
     */
    boolean marcarDeteccionNoConfiable(UUID camaraId, Instant desde);

    /** The agent sees the person again. */
    void marcarDeteccionConfiable(UUID camaraId);

    /** US-22: pauses the camera until {@code hasta} (CA-22.1). */
    Optional<EstadoDeCamara> pausar(UUID camaraId, Instant hasta);

    /** Resumes the camera now. */
    Optional<EstadoDeCamara> reanudar(UUID camaraId);

    /** Ends the camera's pause if it is over (CA-22.3); empty if it did not end now. */
    Optional<EstadoDeCamara> finalizarPausaSiVencio(UUID camaraId, Instant ahora);

    /** Paused cameras whose pause is over, across households: only for the resume job. */
    List<CamaraEnHogar> conPausaVencida(Instant ahora);

    /**
     * @param capturaPermitida a current consent and no active pause (CA-05.2, CA-22.1)
     */
    record CamaraDelHogar(UUID id, String nombreHabitacion, boolean capturaPermitida) {}

    /** The {@code Camara} of the API contract. */
    record EstadoDeCamara(
            UUID id,
            String nombreHabitacion,
            String estadoConexion,
            Instant ultimaSenal,
            Instant pausadaHasta,
            boolean deteccionConfiable,
            Instant instaladaEn,
            Instant noConfiableDesde) {}

    record CamaraEnHogar(UUID camaraId, UUID hogarId) {}
}
