package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.InstalacionRepository;
import tech.tetengo.api.camaras.domain.model.Instalacion;

@Repository
class InstalacionRepositoryAdapter implements InstalacionRepository {

    private final InstalacionJpaRepository jpa;

    InstalacionRepositoryAdapter(InstalacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Instalacion> buscarPorCredencial(String huella) {
        return jpa.findByCredencialHash(huella);
    }

    @Override
    public Instalacion guardar(Instalacion instalacion) {
        return jpa.save(instalacion);
    }
}
