package tech.tetengo.api.hogares.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Invitation validity, a pending team decision (see {@code docs/BLOCKERS.md}). The e-mailed link is
 * {@code tetengo.enlaces.invitacion}, by default the app's {@code /invitacion/{token}} route under
 * {@code tetengo.enlaces.base} (the native app or the PWA).
 */
@ConfigurationProperties("tetengo.invitaciones")
public record PropiedadesDeInvitaciones(Duration vigencia) {}
