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
        return jpa.findByIdAndRetiradaEnIsNull(id);
    }

    @Override
    public List<Camara> listar() {
        return jpa.findByRetiradaEnIsNull(Sort.by("nombreHabitacion"));
    }

    @Override
    public List<Camara> deLaPc(UUID pcId) {
        return jpa.findByPcIdAndRetiradaEnIsNull(pcId, Sort.by("nombreHabitacion"));
    }

    @Override
    public Optional<CamaraDeAgente> deAgente(UUID hogarId, UUID camaraId) {
        return jpa.deAgente(hogarId, camaraId).stream()
                .findFirst()
                .map(fila -> new CamaraDeAgente((UUID) fila[0], (Boolean) fila[1], (Boolean) fila[2]));
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
