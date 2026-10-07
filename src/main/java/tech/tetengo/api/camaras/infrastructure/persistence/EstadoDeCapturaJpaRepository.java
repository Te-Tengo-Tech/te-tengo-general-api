package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;

interface EstadoDeCapturaJpaRepository extends JpaRepository<EstadoDeCaptura, UUID> {

    Optional<EstadoDeCaptura> findFirstBy();
}
