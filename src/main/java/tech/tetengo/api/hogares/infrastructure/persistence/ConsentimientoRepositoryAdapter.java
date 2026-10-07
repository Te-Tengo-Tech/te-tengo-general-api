package tech.tetengo.api.hogares.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.hogares.application.port.ConsentimientoRepository;
import tech.tetengo.api.hogares.domain.model.Consentimiento;

@Repository
class ConsentimientoRepositoryAdapter implements ConsentimientoRepository {

    private final ConsentimientoJpaRepository jpa;

    ConsentimientoRepositoryAdapter(ConsentimientoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Consentimiento guardar(Consentimiento consentimiento) {
        return jpa.save(consentimiento);
    }

    @Override
    public Optional<Consentimiento> ultimo() {
        return jpa.findFirstByOrderByOtorgadoEnDesc();
    }
}
