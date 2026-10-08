package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.monitoreo.application.port.TransmisionEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.TransmisionEnVivo;

@Repository
class TransmisionEnVivoRepositoryAdapter implements TransmisionEnVivoRepository {

    /** Keeps these advisory locks apart from any other use of PostgreSQL's advisory locks. */
    private static final long ESPACIO_DE_BLOQUEOS = 0x7474_6776_6976_6fL;

    private final TransmisionEnVivoJpaRepository jpa;

    TransmisionEnVivoRepositoryAdapter(TransmisionEnVivoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<TransmisionEnVivo> deCamara(UUID camaraId) {
        return jpa.findByCamaraId(camaraId);
    }

    @Override
    public List<TransmisionEnVivo> todas() {
        return jpa.findAll();
    }

    @Override
    public TransmisionEnVivo guardar(TransmisionEnVivo transmision) {
        return jpa.saveAndFlush(transmision);
    }

    @Override
    public void eliminar(TransmisionEnVivo transmision) {
        jpa.delete(transmision);
        jpa.flush();
    }

    @Override
    public Optional<UUID> hogarDeClave(UUID camaraId, String huella) {
        return jpa.hogarDeClave(camaraId, huella).stream().findFirst();
    }

    @Override
    public void bloquear(UUID camaraId) {
        jpa.bloquear(ESPACIO_DE_BLOQUEOS ^ camaraId.getMostSignificantBits() ^ camaraId.getLeastSignificantBits());
    }
}
