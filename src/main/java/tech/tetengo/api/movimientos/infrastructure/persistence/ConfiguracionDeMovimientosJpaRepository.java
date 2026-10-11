package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.movimientos.domain.model.ConfiguracionDeMovimientos;

interface ConfiguracionDeMovimientosJpaRepository extends JpaRepository<ConfiguracionDeMovimientos, UUID> {

    Optional<ConfiguracionDeMovimientos> findFirstBy();
}
