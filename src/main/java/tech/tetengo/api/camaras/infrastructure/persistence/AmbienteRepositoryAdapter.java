package tech.tetengo.api.camaras.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.AmbienteRepository;
import tech.tetengo.api.camaras.domain.model.Ambiente;

@Repository
class AmbienteRepositoryAdapter implements AmbienteRepository {

    private final AmbienteJpaRepository jpa;

    AmbienteRepositoryAdapter(AmbienteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Ambiente guardar(Ambiente ambiente) {
        return jpa.save(ambiente);
    }

    @Override
    public Optional<Ambiente> buscar(UUID id) {
        return jpa.findByIdAndEliminadoEnIsNull(id);
    }

    @Override
    public Optional<Ambiente> porNombre(String nombreNormalizado) {
        return jpa.findFirstByNombreNormalizadoAndEliminadoEnIsNull(nombreNormalizado);
    }

    @Override
    public List<Ambiente> listar() {
        return jpa.findByEliminadoEnIsNullOrderByNombreNormalizadoAsc();
    }
}
