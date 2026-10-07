package tech.tetengo.api.cuentas.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.cuentas.domain.model.SesionDeUsuario;

public interface SesionRepository {

    SesionDeUsuario guardar(SesionDeUsuario sesion);

    Optional<SesionDeUsuario> buscar(UUID id);

    Optional<SesionDeUsuario> buscarPorTokenRefresco(String huella);

    /** Closes every open session of the user, e.g. after a password reset. */
    void cerrarTodas(UUID usuarioId, Instant ahora);
}
