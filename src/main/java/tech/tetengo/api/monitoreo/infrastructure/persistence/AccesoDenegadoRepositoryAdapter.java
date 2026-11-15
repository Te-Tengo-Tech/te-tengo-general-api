package tech.tetengo.api.monitoreo.infrastructure.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.monitoreo.application.port.AccesoDenegadoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoDenegado;
import tech.tetengo.api.monitoreo.domain.model.IntentoDenegado;

@Repository
class AccesoDenegadoRepositoryAdapter implements AccesoDenegadoRepository {

    private final AccesoDenegadoJpaRepository jpa;

    AccesoDenegadoRepositoryAdapter(AccesoDenegadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void registrar(IntentoDenegado intento, UUID hogarId) {
        jpa.registrar(
                UuidCreator.getTimeOrderedEpoch(),
                hogarId,
                intento.ocurridoEn(),
                intento.canal().name(),
                intento.motivo().name(),
                intento.accion(),
                intento.protocolo(),
                intento.camaraId(),
                intento.usuarioId(),
                intento.hogarDelUsuario());
    }

    @Override
    public List<AccesoDenegado> recientesPrimero() {
        return jpa.findAllByOrderByOcurridoEnDescIdDesc();
    }
}
