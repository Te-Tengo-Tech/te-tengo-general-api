package tech.tetengo.api.cuentas.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.cuentas.domain.model.Cuenta;

/** Persistence port for accounts (a global table, not filtered by household). */
public interface CuentaRepository {

    /** Saves the account; throws {@code CORREO_EN_USO} if another account took the e-mail meanwhile. */
    Cuenta guardar(Cuenta cuenta);

    Optional<Cuenta> buscar(UUID id);

    List<Cuenta> buscarTodos(Collection<UUID> ids);

    Optional<Cuenta> buscarPorCorreo(String correoNormalizado);

    boolean existeCorreo(String correoNormalizado);
}
