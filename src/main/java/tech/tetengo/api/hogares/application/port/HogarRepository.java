package tech.tetengo.api.hogares.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.hogares.domain.model.Hogar;

/** Households are a global table: lookups are by the id taken from the token, never from the client. */
public interface HogarRepository {

    Hogar guardar(Hogar hogar);

    Optional<Hogar> buscar(UUID id);

    List<Hogar> buscarTodos(Collection<UUID> ids);
}
