package tech.tetengo.api.hogares.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.hogares.domain.model.Consentimiento;

interface ConsentimientoJpaRepository extends JpaRepository<Consentimiento, UUID> {

    Optional<Consentimiento> findFirstByOrderByOtorgadoEnDesc();
}
