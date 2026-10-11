package tech.tetengo.api.actividad.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.actividad.application.port.ActividadRepository;
import tech.tetengo.api.actividad.domain.model.Actividad;

@Repository
class ActividadRepositoryAdapter implements ActividadRepository {

    private final ActividadJpaRepository jpa;

    ActividadRepositoryAdapter(ActividadJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Actividad registrar(Actividad actividad) {
        return jpa.save(actividad);
    }

    @Override
    public boolean existe(String clave) {
        return jpa.existsByClave(clave);
    }

    @Override
    public Pagina buscar(Filtro filtro, int pagina, int tamano) {
        List<Specification<Actividad>> condiciones = new ArrayList<>();
        if (filtro.pcId() != null) {
            condiciones.add((raiz, consulta, cb) -> cb.equal(raiz.get("pcId"), filtro.pcId()));
        }
        if (filtro.tipo() != null) {
            condiciones.add((raiz, consulta, cb) -> cb.equal(raiz.get("tipo"), filtro.tipo()));
        }
        if (filtro.desde() != null) {
            condiciones.add((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("ocurridaEn"), filtro.desde()));
        }
        if (filtro.hasta() != null) {
            condiciones.add((raiz, consulta, cb) -> cb.lessThan(raiz.get("ocurridaEn"), filtro.hasta()));
        }
        var orden = Sort.by(Sort.Order.desc("ocurridaEn"), Sort.Order.desc("creadoEn"));
        var resultado = jpa.findAll(Specification.allOf(condiciones), PageRequest.of(pagina, tamano, orden));
        return new Pagina(resultado.getContent(), resultado.getTotalElements());
    }
}
