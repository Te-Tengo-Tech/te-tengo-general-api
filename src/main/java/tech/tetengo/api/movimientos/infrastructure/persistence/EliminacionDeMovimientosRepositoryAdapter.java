package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.movimientos.application.port.EliminacionDeMovimientosRepository;
import tech.tetengo.api.movimientos.domain.model.EliminacionDeMovimientos;

@Repository
class EliminacionDeMovimientosRepositoryAdapter implements EliminacionDeMovimientosRepository {

    private final EliminacionDeMovimientosJpaRepository jpa;

    EliminacionDeMovimientosRepositoryAdapter(EliminacionDeMovimientosJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EliminacionDeMovimientos guardar(EliminacionDeMovimientos eliminacion) {
        return jpa.save(eliminacion);
    }

    @Override
    public Optional<EliminacionDeMovimientos> ultima() {
        return jpa.findFirstByOrderByProgramadaEnDesc();
    }

    @Override
    public List<EliminacionDeMovimientos> pendientes() {
        return jpa.findByTerminadaEnIsNull();
    }

    @Override
    public List<UUID> hogaresConPendientes() {
        return jpa.hogaresConPendientes();
    }
}
