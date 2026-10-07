package tech.tetengo.api.alertas.infrastructure.persistence;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    public Pagina<Alerta> buscar(FiltroDeAlertas filtro, int pagina, int tamano) {
        Specification<Alerta> condicion = (raiz, consulta, cb) -> {
            List<Predicate> partes = new ArrayList<>();
            if (filtro.tipo() != null) {
                partes.add(cb.equal(raiz.get("tipo"), filtro.tipo()));
            }
            if (filtro.estado() != null) {
                partes.add(cb.equal(raiz.get("estado"), filtro.estado()));
            }
            if (filtro.desde() != null) {
                partes.add(cb.greaterThanOrEqualTo(raiz.get("ocurridaEn"), filtro.desde()));
            }
            if (filtro.hasta() != null) {
                partes.add(cb.lessThanOrEqualTo(raiz.get("ocurridaEn"), filtro.hasta()));
            }
            return cb.and(partes.toArray(Predicate[]::new));
        };
        Page<Alerta> resultado = jpa.findAll(
                condicion,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Order.desc("ocurridaEn"), Sort.Order.desc("id"))));
        return new Pagina<>(resultado.getContent(), resultado.getTotalElements());
    }

    @Override
    public List<Alerta> conClip() {
        return jpa.findByClipClaveIsNotNullAndClipEliminadoEnIsNull();
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
