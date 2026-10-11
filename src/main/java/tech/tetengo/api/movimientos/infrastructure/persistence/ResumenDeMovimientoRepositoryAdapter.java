package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.movimientos.application.port.ResumenDeMovimientoRepository;
import tech.tetengo.api.movimientos.domain.model.ResumenDiarioDeMovimiento;

@Repository
class ResumenDeMovimientoRepositoryAdapter implements ResumenDeMovimientoRepository {

    private final ResumenDeMovimientoJpaRepository jpa;

    ResumenDeMovimientoRepositoryAdapter(ResumenDeMovimientoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ResumenDiarioDeMovimiento guardar(ResumenDiarioDeMovimiento resumen) {
        return jpa.save(resumen);
    }

    @Override
    public Optional<ResumenDiarioDeMovimiento> deCamaraYDia(UUID camaraId, LocalDate dia) {
        return jpa.findByCamaraIdAndDia(camaraId, dia);
    }

    @Override
    public void eliminarTodos() {
        jpa.deleteAll(jpa.findAll());
    }
}
