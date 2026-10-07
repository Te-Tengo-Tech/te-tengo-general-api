package tech.tetengo.api.hogares;

import java.util.UUID;

/** Public API of {@code hogares}: who gets an alert first and when to escalate it (US-10). */
public interface OrdenDeAviso {

    /** CA-10.2: the shortest wait the owner can choose before escalating (3, 5 or 10 minutes). */
    int ESPERA_MINIMA_MINUTOS = 3;

    Configuracion de(UUID hogarId);

    /** {@code secundarioId} is null when there is nobody to escalate to (CA-10.4, CA-20.3). */
    record Configuracion(UUID principalId, UUID secundarioId, int esperaMinutos) {}
}
