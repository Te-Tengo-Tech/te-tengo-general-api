package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.movimientos.application.port.RepeticionRepository;
import tech.tetengo.api.movimientos.domain.model.RepeticionDeMovimiento;

@Repository
class RepeticionRepositoryAdapter implements RepeticionRepository {

    private final RepeticionJpaRepository jpa;

    RepeticionRepositoryAdapter(RepeticionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RepeticionDeMovimiento guardar(RepeticionDeMovimiento repeticion) {
        return jpa.save(repeticion);
    }

    @Override
    public Optional<RepeticionDeMovimiento> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<RepeticionDeMovimiento> recientesPrimero() {
        return jpa.findAllByOrderByInicioDesc();
    }
}
