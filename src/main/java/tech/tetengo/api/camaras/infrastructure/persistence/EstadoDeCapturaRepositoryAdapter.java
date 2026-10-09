package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.EstadoDeCapturaRepository;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;

@Repository
class EstadoDeCapturaRepositoryAdapter implements EstadoDeCapturaRepository {

    private final EstadoDeCapturaJpaRepository jpa;

    EstadoDeCapturaRepositoryAdapter(EstadoDeCapturaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EstadoDeCaptura guardar(EstadoDeCaptura estado) {
        return jpa.save(estado);
    }

    @Override
    public Optional<EstadoDeCaptura> actual() {
        return jpa.findFirstBy();
    }
}
