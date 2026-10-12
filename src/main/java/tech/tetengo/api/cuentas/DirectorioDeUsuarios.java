package tech.tetengo.api.cuentas;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of {@code cuentas}: names, e-mails and profiles of users, for other modules' responses.
 */
public interface DirectorioDeUsuarios {

    Optional<Usuario> buscar(UUID id);

    /** By e-mail, compared trimmed and case-insensitively. */
    Optional<Usuario> buscarPorCorreo(String correo);

    /** Users found, by id; unknown ids are left out. */
    Map<UUID, Usuario> buscarTodos(Collection<UUID> ids);

    /**
     * @param telefono null when not given (API contract §13)
     * @param parentesco null when not given
     * @param fotoClave object key of the user's photo, null without one
     */
    record Usuario(UUID id, String nombre, String correo, String telefono, String parentesco, String fotoClave) {

        public Usuario(UUID id, String nombre, String correo) {
            this(id, nombre, correo, null, null, null);
        }
    }
}
