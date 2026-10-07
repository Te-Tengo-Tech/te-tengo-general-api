package tech.tetengo.api.historial.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retention of the recordings (US-26). The backlog mentions a retention policy (CA-26.3) but not its
 * length, so it stays unset until the team decides (see {@code docs/BLOCKERS.md}); unset means clips
 * are kept.
 */
@ConfigurationProperties("tetengo.historial")
public record PropiedadesDeHistorial(Duration retencionDeClips) {}
