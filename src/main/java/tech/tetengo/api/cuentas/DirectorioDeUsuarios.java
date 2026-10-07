package tech.tetengo.api.cuentas;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public API of {@code cuentas}: names and e-mails of users, for other modules' responses. */
public interface DirectorioDeUsuarios {

    Optional<Usuario> buscar(UUID id);

    /** Users found, by id; unknown ids are left out. */
    Map<UUID, Usuario> buscarTodos(Collection<UUID> ids);

    record Usuario(UUID id, String nombre, String correo) {}
}
