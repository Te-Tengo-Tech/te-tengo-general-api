package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.camaras.domain.model.Ambiente;

interface AmbienteJpaRepository extends JpaRepository<Ambiente, UUID> {

    Optional<Ambiente> findByIdAndEliminadoEnIsNull(UUID id);

    Optional<Ambiente> findFirstByNombreNormalizadoAndEliminadoEnIsNull(String nombreNormalizado);

    List<Ambiente> findByEliminadoEnIsNullOrderByNombreNormalizadoAsc();
}
