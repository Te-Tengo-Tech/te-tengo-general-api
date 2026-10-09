package tech.tetengo.api.cuentas;

import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.Rol;

/**
 * Provided by {@code hogares}, which owns the household–user memberships, so {@code cuentas} can pick
 * the household of a new session without depending on that module.
 */
public interface MembresiasDeUsuario {

    /** Household used at sign-in: the one the user owns, otherwise the first one they joined. */
    Optional<HogarDelUsuario> hogarPredeterminado(UUID usuarioId);

    /** The user's role in the household, empty if they are not (or no longer) a member. */
    Optional<Rol> rolEn(UUID usuarioId, UUID hogarId);

    record HogarDelUsuario(UUID hogarId, Rol rol) {}
}
