package tech.tetengo.api.movimientos.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.movimientos.domain.model.EliminacionDeMovimientos;

public interface EliminacionDeMovimientosRepository {

    EliminacionDeMovimientos guardar(EliminacionDeMovimientos eliminacion);

    Optional<EliminacionDeMovimientos> ultima();

    List<EliminacionDeMovimientos> pendientes();

    /** Households with pending deletions: a native query, only for the job. */
    List<UUID> hogaresConPendientes();
}
