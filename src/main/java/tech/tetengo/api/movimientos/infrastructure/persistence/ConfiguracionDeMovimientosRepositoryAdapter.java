package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.movimientos.application.port.ConfiguracionDeMovimientosRepository;
import tech.tetengo.api.movimientos.domain.model.ConfiguracionDeMovimientos;

@Repository
class ConfiguracionDeMovimientosRepositoryAdapter implements ConfiguracionDeMovimientosRepository {

    private final ConfiguracionDeMovimientosJpaRepository jpa;

    ConfiguracionDeMovimientosRepositoryAdapter(ConfiguracionDeMovimientosJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ConfiguracionDeMovimientos guardar(ConfiguracionDeMovimientos configuracion) {
        return jpa.save(configuracion);
    }

    @Override
    public Optional<ConfiguracionDeMovimientos> actual() {
        return jpa.findFirstBy();
    }
}
