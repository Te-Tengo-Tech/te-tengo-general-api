package tech.tetengo.api.camaras.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.Camara;

/** Persistence port. Queries are already filtered by the current household (multi-tenancy). */
public interface CamaraRepository {

    Camara guardar(Camara camara);

    Optional<Camara> buscar(UUID id);

    List<Camara> listar();

    /**
     * Online cameras of every household whose last heartbeat is older than {@code limite}. A native
     * query across households, only for the disconnection job (see {@code MULTITENANCY.md}).
     */
    List<CamaraDeUnHogar> enLineaSinSenalDesde(Instant limite);

    record CamaraDeUnHogar(UUID camaraId, UUID hogarId) {}
}
