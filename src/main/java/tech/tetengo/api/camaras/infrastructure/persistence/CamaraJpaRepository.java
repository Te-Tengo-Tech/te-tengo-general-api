package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.camaras.domain.model.Camara;

/** Internal to infrastructure: use cases depend on the {@code CamaraRepository} port. */
interface CamaraJpaRepository extends JpaRepository<Camara, UUID> {}
