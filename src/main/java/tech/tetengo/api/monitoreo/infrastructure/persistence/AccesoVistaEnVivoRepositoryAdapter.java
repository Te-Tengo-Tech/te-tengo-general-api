package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;

@Repository
class AccesoVistaEnVivoRepositoryAdapter implements AccesoVistaEnVivoRepository {

    private final AccesoVistaEnVivoJpaRepository jpa;

    AccesoVistaEnVivoRepositoryAdapter(AccesoVistaEnVivoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AccesoVistaEnVivo guardar(AccesoVistaEnVivo acceso) {
        return jpa.save(acceso);
    }

    @Override
    public Optional<AccesoVistaEnVivo> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<AccesoVistaEnVivo> recientesPrimero() {
        return jpa.findAllByOrderByInicioDesc();
    }

    @Override
    public Optional<SesionDeToken> porToken(String huella) {
        return jpa.porToken(huella).stream().findFirst().map(fila -> new SesionDeToken((UUID) fila[0], (UUID) fila[1]));
    }
}
