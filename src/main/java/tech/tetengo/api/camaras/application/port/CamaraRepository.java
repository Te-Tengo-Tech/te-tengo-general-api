package tech.tetengo.api.camaras.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.Camara;

/** Persistence port. Queries are already filtered by the current household (multi-tenancy). */
public interface CamaraRepository {

    Camara guardar(Camara camara);

    Optional<Camara> buscar(UUID id);

    List<Camara> listar();
}
