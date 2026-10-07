package tech.tetengo.api.camaras.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.domain.model.Camara;

@Repository
class CamaraRepositoryAdapter implements CamaraRepository {

    private final CamaraJpaRepository jpa;

    CamaraRepositoryAdapter(CamaraJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Camara guardar(Camara camara) {
        return jpa.save(camara);
    }

    @Override
    public Optional<Camara> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Camara> listar() {
        return jpa.findAll(Sort.by("nombreHabitacion"));
    }

    @Override
    public List<CamaraDeUnHogar> conPausaVencida(Instant ahora) {
        return jpa.conPausaVencida(ahora).stream()
                .map(fila -> new CamaraDeUnHogar((UUID) fila[0], (UUID) fila[1]))
                .toList();
    }

    @Override
    public List<CamaraDeUnHogar> enLineaSinSenalDesde(Instant limite) {
        return jpa.enLineaSinSenalDesde(limite).stream()
                .map(fila -> new CamaraDeUnHogar((UUID) fila[0], (UUID) fila[1]))
                .toList();
    }
}
