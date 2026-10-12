package tech.tetengo.api.cuentas;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of {@code cuentas}: sets or removes a user's photo (API contract §13). The photo itself is
 * processed and stored by {@code hogares}, which owns the uploads; this only records its key.
 */
public interface FotosDeCuentas {

    /** Sets the user's photo; returns the key of the one it replaces, to delete it. */
    Optional<String> cambiar(UUID usuarioId, String clave);

    /** Removes the user's photo; returns its key, to delete it, or empty if there was none. */
    Optional<String> quitar(UUID usuarioId);
}
