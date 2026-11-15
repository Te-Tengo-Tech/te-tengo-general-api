package tech.tetengo.api.monitoreo.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;

/** Live view sessions of the household in context, except the two native lookups below. */
public interface AccesoVistaEnVivoRepository {

    AccesoVistaEnVivo guardar(AccesoVistaEnVivo acceso);

    Optional<AccesoVistaEnVivo> buscar(UUID id);

    /** CA-24.2: the household's accesses, newest first. */
    List<AccesoVistaEnVivo> recientesPrimero();

    /** Sessions of the household that have not ended. */
    List<AccesoVistaEnVivo> abiertas();

    /** Sessions of the camera that have not ended. */
    List<AccesoVistaEnVivo> abiertasDe(UUID camaraId);

    /**
     * The session and household of a viewer token: a native query, because MediaMTX's authorization
     * request carries no JWT. The session is then loaded inside its household.
     */
    Optional<SesionDeToken> porToken(String huella);

    /** Households with sessions that have not ended: a native query, only for the session end job. */
    List<UUID> hogaresConSesionesAbiertas();

    /**
     * Who opened a session and on which camera, whatever its household: a native query, only for the
     * denied access log (ADR 0015), when a member names a session that is not theirs.
     */
    Optional<SesionDeCualquierHogar> deCualquierHogar(UUID sesionId);

    record SesionDeToken(UUID sesionId, UUID hogarId) {}

    record SesionDeCualquierHogar(UUID camaraId, UUID usuarioId) {}
}
