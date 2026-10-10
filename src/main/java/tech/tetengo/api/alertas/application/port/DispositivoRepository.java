package tech.tetengo.api.alertas.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.Dispositivo;

public interface DispositivoRepository {

    Dispositivo guardar(Dispositivo dispositivo);

    Optional<Dispositivo> buscarPorToken(String tokenPush);

    Optional<Dispositivo> buscar(UUID id);

    /** Number of active devices of the users. */
    long activosDe(Collection<UUID> usuarioIds);

    /** Active devices of the users. */
    List<Dispositivo> deUsuarios(Collection<UUID> usuarioIds);

    void eliminar(Dispositivo dispositivo);
}
