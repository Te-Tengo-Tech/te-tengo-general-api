package tech.tetengo.api.camaras.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.model.EstadoPc;
import tech.tetengo.api.camaras.domain.model.Pc;

@Repository
class PcRepositoryAdapter implements PcRepository {

    private final PcJpaRepository jpa;

    PcRepositoryAdapter(PcJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Pc guardar(Pc pc) {
        return jpa.save(pc);
    }

    @Override
    public Optional<Pc> buscar(UUID id) {
        return jpa.findByIdAndEstado(id, EstadoPc.VINCULADA);
    }

    @Override
    public Optional<Pc> buscarParaActualizar(UUID id) {
        return jpa.findByIdAndEstadoForUpdate(id, EstadoPc.VINCULADA);
    }

    @Override
    public List<Pc> vinculadas() {
        return jpa.findByEstadoOrderByVinculadaEnAsc(EstadoPc.VINCULADA);
    }

    @Override
    public boolean vinculada(UUID hogarId, UUID pcId) {
        return jpa.vinculada(hogarId, pcId);
    }

    @Override
    public List<PcDeUnHogar> sinUsoDesde(Instant limite) {
        return jpa.sinUsoDesde(limite).stream()
                .map(fila -> new PcDeUnHogar((UUID) fila[0], (UUID) fila[1]))
                .toList();
    }
}
