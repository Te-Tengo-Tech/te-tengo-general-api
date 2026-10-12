package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.movimientos.domain.model.ResumenDiarioDeMovimiento;

interface ResumenDeMovimientoJpaRepository extends JpaRepository<ResumenDiarioDeMovimiento, UUID> {

    Optional<ResumenDiarioDeMovimiento> findByCamaraIdAndDia(UUID camaraId, LocalDate dia);
}
