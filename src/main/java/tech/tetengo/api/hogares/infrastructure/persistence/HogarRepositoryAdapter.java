package tech.tetengo.api.hogares.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.domain.model.Hogar;

@Repository
class HogarRepositoryAdapter implements HogarRepository {

    private final HogarJpaRepository jpa;

    HogarRepositoryAdapter(HogarJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Hogar guardar(Hogar hogar) {
        return jpa.save(hogar);
    }

    @Override
    public Optional<Hogar> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Hogar> buscarTodos(Collection<UUID> ids) {
        return jpa.findAllById(ids);
    }
}
