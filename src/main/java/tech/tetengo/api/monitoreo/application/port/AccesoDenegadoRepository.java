package tech.tetengo.api.monitoreo.application.port;

import java.util.List;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.AccesoDenegado;
import tech.tetengo.api.monitoreo.domain.model.IntentoDenegado;

/** The denied access log of live view (ADR 0015). */
public interface AccesoDenegadoRepository {

    /**
     * Records the attempt under {@code hogarId}, the household of the camera asked for (null when the
     * camera exists nowhere): a native insert, because the household in context, if any, is the
     * requester's.
     */
    void registrar(IntentoDenegado intento, UUID hogarId);

    /** The denials on the cameras of the household in context, newest first. */
    List<AccesoDenegado> recientesPrimero();
}
