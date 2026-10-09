package tech.tetengo.api.cuentas.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.cuentas.application.port.SesionRepository;
import tech.tetengo.api.cuentas.domain.model.SesionDeUsuario;

@Repository
class SesionRepositoryAdapter implements SesionRepository {

    private final SesionJpaRepository jpa;

    SesionRepositoryAdapter(SesionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public SesionDeUsuario guardar(SesionDeUsuario sesion) {
        return jpa.save(sesion);
    }

    @Override
    public Optional<SesionDeUsuario> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<SesionDeUsuario> buscarPorTokenRefresco(String huella) {
        return jpa.findByTokenRefrescoHash(huella);
    }

    @Override
    public void cerrarTodas(UUID usuarioId, Instant ahora) {
        jpa.cerrarTodas(usuarioId, ahora);
    }
}
