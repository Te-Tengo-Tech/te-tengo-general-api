package tech.tetengo.api.alertas.application.port;

import java.util.List;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.EliminacionDeGrabaciones;

public interface EliminacionDeGrabacionesRepository {

    EliminacionDeGrabaciones guardar(EliminacionDeGrabaciones eliminacion);

    /** Pending deletions of the household in context. */
    List<EliminacionDeGrabaciones> pendientes();

    /** Households with pending deletions: a native query across households, only for the job. */
    List<UUID> hogaresConPendientes();
}
