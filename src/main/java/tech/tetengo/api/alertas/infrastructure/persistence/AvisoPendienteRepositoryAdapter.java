package tech.tetengo.api.alertas.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.alertas.application.port.AvisoPendienteRepository;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;

@Repository
class AvisoPendienteRepositoryAdapter implements AvisoPendienteRepository {

    private final AvisoPendienteJpaRepository jpa;

    AvisoPendienteRepositoryAdapter(AvisoPendienteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AvisoPendiente guardar(AvisoPendiente aviso) {
        return jpa.save(aviso);
    }

    @Override
    public Optional<AvisoPendiente> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<AvisoPendiente> delHogar() {
        return jpa.findAll();
    }

    @Override
    public List<AvisoPendiente> vencidos(Instant ahora) {
        return jpa.findByProximoIntentoLessThanEqualOrderByCreadoEnAsc(ahora);
    }

    @Override
    public void eliminar(AvisoPendiente aviso) {
        jpa.delete(aviso);
    }

    @Override
    public List<UUID> hogaresConVencidos(Instant ahora) {
        return jpa.hogaresConVencidos(ahora);
    }
}
