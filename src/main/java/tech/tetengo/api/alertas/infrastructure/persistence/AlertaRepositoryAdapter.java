package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EstadoAlerta;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;

@Repository
class AlertaRepositoryAdapter implements AlertaRepository {

    private final AlertaJpaRepository jpa;

    AlertaRepositoryAdapter(AlertaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Alerta guardar(Alerta alerta) {
        return jpa.save(alerta);
    }

    @Override
    public Optional<Alerta> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Alerta> inestableActivaDe(UUID camaraId) {
        return jpa.findFirstByCamaraIdAndTipoAndEstadoOrderByOcurridaEnDesc(
                camaraId, TipoAlerta.MOVIMIENTO_INESTABLE, EstadoAlerta.ACTIVA);
    }

    @Override
    public Optional<Alerta> caidaPorConfirmarDe(UUID camaraId) {
        return jpa.findFirstByCamaraIdAndTipoAndEstadoAndConfirmadaFalseAndRecuperadaEnIsNullOrderByOcurridaEnDesc(
                camaraId, TipoAlerta.CAIDA, EstadoAlerta.ACTIVA);
    }

    @Override
    public Optional<Alerta> caidaSinRecuperacionDe(UUID camaraId) {
        return jpa.findFirstByCamaraIdAndTipoAndRecuperadaEnIsNullOrderByOcurridaEnDesc(camaraId, TipoAlerta.CAIDA);
    }
}
