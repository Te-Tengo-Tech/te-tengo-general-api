package tech.tetengo.api.hogares.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Invitation validity and the e-mailed link ({@code {token}} is replaced). Both are pending team
 * decisions (see {@code docs/BLOCKERS.md}).
 */
@ConfigurationProperties("tetengo.invitaciones")
public record PropiedadesDeInvitaciones(Duration vigencia) {}
