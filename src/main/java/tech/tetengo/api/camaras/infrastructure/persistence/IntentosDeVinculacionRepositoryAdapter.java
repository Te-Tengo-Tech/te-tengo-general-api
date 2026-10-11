package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.IntentosDeVinculacionRepository;
import tech.tetengo.api.camaras.domain.model.IntentosDeVinculacion;

@Repository
class IntentosDeVinculacionRepositoryAdapter implements IntentosDeVinculacionRepository {

    private final IntentosDeVinculacionJpaRepository jpa;

    IntentosDeVinculacionRepositoryAdapter(IntentosDeVinculacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public IntentosDeVinculacion guardar(IntentosDeVinculacion intentos) {
        return jpa.save(intentos);
    }

    @Override
    public Optional<IntentosDeVinculacion> deUsuarioParaActualizar(UUID usuarioId) {
        return jpa.findByUsuarioIdForUpdate(usuarioId);
    }
}
