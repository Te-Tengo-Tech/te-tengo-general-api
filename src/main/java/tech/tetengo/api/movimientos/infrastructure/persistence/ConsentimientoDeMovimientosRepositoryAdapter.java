package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.movimientos.application.port.ConsentimientoDeMovimientosRepository;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;

@Repository
class ConsentimientoDeMovimientosRepositoryAdapter implements ConsentimientoDeMovimientosRepository {

    private final ConsentimientoDeMovimientosJpaRepository jpa;

    ConsentimientoDeMovimientosRepositoryAdapter(ConsentimientoDeMovimientosJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ConsentimientoDeMovimientos guardar(ConsentimientoDeMovimientos consentimiento) {
        return jpa.save(consentimiento);
    }

    @Override
    public Optional<ConsentimientoDeMovimientos> ultimo() {
        return jpa.findFirstByOrderByOtorgadoEnDescCreadoEnDesc();
    }
}
