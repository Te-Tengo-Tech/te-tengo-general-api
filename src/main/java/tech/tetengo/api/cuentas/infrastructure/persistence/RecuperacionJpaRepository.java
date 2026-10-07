package tech.tetengo.api.cuentas.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.cuentas.domain.model.Recuperacion;

interface RecuperacionJpaRepository extends JpaRepository<Recuperacion, UUID> {

    Optional<Recuperacion> findByTokenHash(String tokenHash);
}
