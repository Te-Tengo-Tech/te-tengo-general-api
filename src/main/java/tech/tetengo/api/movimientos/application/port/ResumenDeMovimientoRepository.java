package tech.tetengo.api.movimientos.application.port;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.movimientos.domain.model.ResumenDiarioDeMovimiento;

public interface ResumenDeMovimientoRepository {

    ResumenDiarioDeMovimiento guardar(ResumenDiarioDeMovimiento resumen);

    Optional<ResumenDiarioDeMovimiento> deCamaraYDia(UUID camaraId, LocalDate dia);

    /** Deletes the household's summaries (with its movements). */
    void eliminarTodos();
}
