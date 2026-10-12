package tech.tetengo.api.hogares.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.hogares.application.port.SubidaDeFotoRepository;
import tech.tetengo.api.hogares.domain.model.SubidaDeFoto;

@Repository
class SubidaDeFotoRepositoryAdapter implements SubidaDeFotoRepository {

    private final SubidaDeFotoJpaRepository jpa;

    SubidaDeFotoRepositoryAdapter(SubidaDeFotoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public SubidaDeFoto guardar(SubidaDeFoto subida) {
        return jpa.save(subida);
    }

    @Override
    public Optional<SubidaDeFoto> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public void eliminar(SubidaDeFoto subida) {
        jpa.delete(subida);
    }

    @Override
    public List<SubidaDeFoto> pedidasAntesDe(Instant instante, int limite) {
        return jpa.findByPedidaEnBeforeOrderByPedidaEn(instante, Limit.of(limite));
    }
}
