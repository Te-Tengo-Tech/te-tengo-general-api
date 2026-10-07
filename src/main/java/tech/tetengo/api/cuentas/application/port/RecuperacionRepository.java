package tech.tetengo.api.cuentas.application.port;

import java.util.Optional;
import tech.tetengo.api.cuentas.domain.model.Recuperacion;

public interface RecuperacionRepository {

    Recuperacion guardar(Recuperacion recuperacion);

    Optional<Recuperacion> buscarPorToken(String huella);
}
