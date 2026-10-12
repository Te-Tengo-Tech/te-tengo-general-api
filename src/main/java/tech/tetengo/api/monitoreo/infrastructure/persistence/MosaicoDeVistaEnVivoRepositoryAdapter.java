package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.monitoreo.application.port.MosaicoDeVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.MosaicoDeVistaEnVivo;

@Repository
class MosaicoDeVistaEnVivoRepositoryAdapter implements MosaicoDeVistaEnVivoRepository {

    private final MosaicoDeVistaEnVivoJpaRepository jpa;

    MosaicoDeVistaEnVivoRepositoryAdapter(MosaicoDeVistaEnVivoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public MosaicoDeVistaEnVivo guardar(MosaicoDeVistaEnVivo mosaico) {
        return jpa.save(mosaico);
    }

    @Override
    public Optional<MosaicoDeVistaEnVivo> buscar(UUID id) {
        return jpa.findById(id);
    }
}
