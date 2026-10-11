package tech.tetengo.api.camaras.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.camaras.domain.model.IntentosDeVinculacion;

interface IntentosDeVinculacionJpaRepository extends JpaRepository<IntentosDeVinculacion, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IntentosDeVinculacion i where i.usuarioId = :usuarioId")
    Optional<IntentosDeVinculacion> findByUsuarioIdForUpdate(UUID usuarioId);
}
