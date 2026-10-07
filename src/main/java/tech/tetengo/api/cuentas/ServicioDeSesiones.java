package tech.tetengo.api.cuentas;

import java.util.UUID;
import tech.tetengo.api.shared.domain.model.Rol;

/** Public API of {@code cuentas}: other modules open sessions without touching its internals. */
public interface ServicioDeSesiones {

    /**
     * Opens a session of the user for the household (or none when both are null) and closes the
     * session it replaces, if any (e.g. after creating or switching households).
     */
    Sesion abrir(UUID usuarioId, UUID hogarId, Rol rol, UUID sesionQueReemplaza);
}
