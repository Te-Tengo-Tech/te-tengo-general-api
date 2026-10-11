package tech.tetengo.api.movimientos.application.port;

import java.util.Optional;
import tech.tetengo.api.movimientos.domain.model.ConfiguracionDeMovimientos;

/** One row per household. */
public interface ConfiguracionDeMovimientosRepository {

    ConfiguracionDeMovimientos guardar(ConfiguracionDeMovimientos configuracion);

    Optional<ConfiguracionDeMovimientos> actual();
}
