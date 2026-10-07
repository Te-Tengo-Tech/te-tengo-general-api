package tech.tetengo.api.camaras.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Household agent settings (implementation choices documented in {@code AGENT_CONTRACT.md}): token
 * lifetime, heartbeat interval, how many missed heartbeats mean the camera is disconnected, and the
 * remote configuration ({@code GET /api/agente/configuracion}).
 *
 * @param versionPublicada the agent release the team publishes; the agent only logs a difference
 * @param umbrales classification thresholds by the field names of the agent's {@code Umbrales}.
 *     Empty by default: the agent keeps the thresholds calibrated at installation
 */
@ConfigurationProperties("tetengo.agente")
public record PropiedadesDelAgente(
        Duration vigenciaToken,
        Duration intervaloSenal,
        int senalesPerdidas,
        String versionPublicada,
        Map<String, BigDecimal> umbrales) {

    public PropiedadesDelAgente {
        versionPublicada = versionPublicada == null ? "" : versionPublicada;
        umbrales = umbrales == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(umbrales));
    }

    /** No heartbeat for this long and the camera is disconnected (3 × 30 s). */
    public Duration esperaSinSenal() {
        return intervaloSenal.multipliedBy(senalesPerdidas);
    }
}
