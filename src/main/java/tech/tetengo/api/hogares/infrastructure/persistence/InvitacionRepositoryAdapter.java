package tech.tetengo.api.hogares.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.hogares.application.port.InvitacionRepository;
import tech.tetengo.api.hogares.domain.model.Invitacion;

@Repository
class InvitacionRepositoryAdapter implements InvitacionRepository {

    private final InvitacionJpaRepository jpa;

    InvitacionRepositoryAdapter(InvitacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Invitacion guardar(Invitacion invitacion) {
        return jpa.save(invitacion);
    }

    @Override
    public Optional<Invitacion> buscarPorToken(String huella) {
        return jpa.findByTokenHash(huella);
    }
}
