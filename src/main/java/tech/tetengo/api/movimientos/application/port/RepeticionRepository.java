package tech.tetengo.api.movimientos.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.movimientos.domain.model.RepeticionDeMovimiento;

public interface RepeticionRepository {

    RepeticionDeMovimiento guardar(RepeticionDeMovimiento repeticion);

    Optional<RepeticionDeMovimiento> buscar(UUID id);

    /** CA-39.3: the household's replays, newest first. */
    List<RepeticionDeMovimiento> recientesPrimero();
}
