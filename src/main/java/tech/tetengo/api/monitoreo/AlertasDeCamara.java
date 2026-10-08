package tech.tetengo.api.monitoreo;

import java.util.UUID;

/**
 * Provided by {@code alertas}, which owns the alerts, so a live view opened from an alert (CA-23.2) can
 * check the alert is of that camera without {@code monitoreo} depending on that module.
 */
public interface AlertasDeCamara {

    /** True if the alert exists in the household in context and was raised by that camera. */
    boolean esDeLaCamara(UUID alertaId, UUID camaraId);
}
