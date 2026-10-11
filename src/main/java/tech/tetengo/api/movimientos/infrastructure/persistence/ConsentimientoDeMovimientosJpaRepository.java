package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;

interface ConsentimientoDeMovimientosJpaRepository extends JpaRepository<ConsentimientoDeMovimientos, UUID> {

    Optional<ConsentimientoDeMovimientos> findFirstByOrderByOtorgadoEnDescCreadoEnDesc();
}
