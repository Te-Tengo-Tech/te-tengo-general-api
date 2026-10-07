package tech.tetengo.api.monitoreo.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;

public interface AccesoVistaEnVivoRepository {

    AccesoVistaEnVivo guardar(AccesoVistaEnVivo acceso);

    Optional<AccesoVistaEnVivo> buscar(UUID id);

    /** CA-24.2: the household's accesses, newest first. */
    List<AccesoVistaEnVivo> recientesPrimero();

    /**
     * The session and household of a stream token: a native query, because the stream connection
     * carries no JWT. The session is then loaded inside its household.
     */
    Optional<SesionDeToken> porToken(String huella);

    record SesionDeToken(UUID sesionId, UUID hogarId) {}
}
