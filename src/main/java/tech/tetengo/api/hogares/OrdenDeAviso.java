package tech.tetengo.api.hogares;

import java.util.UUID;

/** Public API of {@code hogares}: who gets an alert first and when to escalate it (US-10). */
public interface OrdenDeAviso {

    Configuracion de(UUID hogarId);

    /** {@code secundarioId} is null when there is nobody to escalate to (CA-10.4, CA-20.3). */
    record Configuracion(UUID principalId, UUID secundarioId, int esperaMinutos) {}
}
