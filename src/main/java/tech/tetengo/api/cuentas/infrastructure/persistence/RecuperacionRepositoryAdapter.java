package tech.tetengo.api.cuentas.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.cuentas.application.port.RecuperacionRepository;
import tech.tetengo.api.cuentas.domain.model.Recuperacion;

@Repository
class RecuperacionRepositoryAdapter implements RecuperacionRepository {

    private final RecuperacionJpaRepository jpa;

    RecuperacionRepositoryAdapter(RecuperacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Recuperacion guardar(Recuperacion recuperacion) {
        return jpa.save(recuperacion);
    }

    @Override
    public Optional<Recuperacion> buscarPorToken(String huella) {
        return jpa.findByTokenHash(huella);
    }
}
