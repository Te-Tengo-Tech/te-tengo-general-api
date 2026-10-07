package tech.tetengo.api.cuentas.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Link sent by e-mail to reset the password; {@code {token}} is replaced by the one-time token. The
 * app's deep-link format is pending (see {@code docs/BLOCKERS.md}).
 */
@ConfigurationProperties("tetengo.enlaces")
public record PropiedadesDeEnlaces(String recuperacion) {

    public String recuperacion(String token) {
        return recuperacion.replace("{token}", token);
    }
}
