package tech.tetengo.api.historial.application;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.RetencionDeClips;

/**
 * US-26 / CA-26.3: scheduled job that deletes recordings older than the retention period. Idempotent:
 * a deleted clip is not deleted again. Does nothing while the period is not configured.
 */
@Service
public class AplicarRetencionDeGrabaciones {

    private static final Logger log = LoggerFactory.getLogger(AplicarRetencionDeGrabaciones.class);

    private final RetencionDeClips retencion;
    private final PropiedadesDeHistorial propiedades;
    private final Clock reloj;

    public AplicarRetencionDeGrabaciones(RetencionDeClips retencion, PropiedadesDeHistorial propiedades, Clock reloj) {
        this.retencion = retencion;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.historial.revision-de-retencion:PT1H}")
    public void ejecutar() {
        if (propiedades.retencionDeClips() == null) {
            return;
        }
        aplicar(propiedades.retencionDeClips());
    }

    /** Deletes the clips of alerts older than {@code periodo}; returns how many. */
    public int aplicar(Duration periodo) {
        int eliminados = retencion.eliminarAnterioresA(reloj.instant().minus(periodo));
        if (eliminados > 0) {
            log.info("Retención: se eliminaron {} grabaciones", eliminados);
        }
        return eliminados;
    }
}
