package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.alertas.application.port.EliminacionDeGrabacionesRepository;
import tech.tetengo.api.alertas.domain.model.EliminacionDeGrabaciones;

@Repository
class EliminacionDeGrabacionesRepositoryAdapter implements EliminacionDeGrabacionesRepository {

    private final EliminacionDeGrabacionesJpaRepository jpa;

    EliminacionDeGrabacionesRepositoryAdapter(EliminacionDeGrabacionesJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EliminacionDeGrabaciones guardar(EliminacionDeGrabaciones eliminacion) {
        return jpa.save(eliminacion);
    }

    @Override
    public List<EliminacionDeGrabaciones> pendientes() {
        return jpa.findByCompletadaEnIsNullOrderBySolicitadaEnAsc();
    }

    @Override
    public Optional<EliminacionDeGrabaciones> ultima() {
        return jpa.findFirstByOrderBySolicitadaEnDescCreadoEnDesc();
    }

    @Override
    public List<UUID> hogaresConPendientes() {
        return jpa.hogaresConPendientes();
    }
}
