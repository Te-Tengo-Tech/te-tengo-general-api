package tech.tetengo.api.camaras.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Household agent settings (implementation choices documented in {@code AGENT_CONTRACT.md}): token
 * lifetime, heartbeat interval and how many missed heartbeats mean the camera is disconnected.
 */
@ConfigurationProperties("tetengo.agente")
public record PropiedadesDelAgente(Duration vigenciaToken, Duration intervaloSenal, int senalesPerdidas) {

    /** No heartbeat for this long and the camera is disconnected (3 × 30 s). */
    public Duration esperaSinSenal() {
        return intervaloSenal.multipliedBy(senalesPerdidas);
    }
}
