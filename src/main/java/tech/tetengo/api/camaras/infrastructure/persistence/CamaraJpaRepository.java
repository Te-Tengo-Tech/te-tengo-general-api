package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.camaras.domain.model.Camara;

/** Interno a la infraestructura: los casos de uso usan el puerto {@code CamaraRepository}. */
interface CamaraJpaRepository extends JpaRepository<Camara, UUID> {}
