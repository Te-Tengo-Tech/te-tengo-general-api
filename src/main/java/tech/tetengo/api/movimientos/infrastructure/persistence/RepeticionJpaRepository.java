package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.movimientos.domain.model.RepeticionDeMovimiento;

interface RepeticionJpaRepository extends JpaRepository<RepeticionDeMovimiento, UUID> {

    List<RepeticionDeMovimiento> findAllByOrderByInicioDesc();
}
