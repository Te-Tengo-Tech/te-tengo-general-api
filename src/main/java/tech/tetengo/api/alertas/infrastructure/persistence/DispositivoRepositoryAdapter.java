package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.alertas.application.port.DispositivoRepository;
import tech.tetengo.api.alertas.domain.model.Dispositivo;

@Repository
class DispositivoRepositoryAdapter implements DispositivoRepository {

    private final DispositivoJpaRepository jpa;

    DispositivoRepositoryAdapter(DispositivoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Dispositivo guardar(Dispositivo dispositivo) {
        return jpa.save(dispositivo);
    }

    @Override
    public Optional<Dispositivo> buscarPorToken(String tokenPush) {
        return jpa.findByTokenPush(tokenPush);
    }

    @Override
    public Optional<Dispositivo> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public long activosDe(Collection<UUID> usuarioIds) {
        return usuarioIds.isEmpty() ? 0 : jpa.countByUsuarioIdInAndActivoTrue(usuarioIds);
    }

    @Override
    public List<Dispositivo> deUsuarios(Collection<UUID> usuarioIds) {
        return jpa.findByUsuarioIdInAndActivoTrue(usuarioIds);
    }

    @Override
    public void eliminar(Dispositivo dispositivo) {
        jpa.delete(dispositivo);
    }
}
