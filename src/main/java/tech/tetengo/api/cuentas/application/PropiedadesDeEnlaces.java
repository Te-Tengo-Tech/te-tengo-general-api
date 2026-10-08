package tech.tetengo.api.cuentas.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Link sent by e-mail to reset the password; {@code {token}} is replaced by the one-time token. It
 * opens the app's {@code /nueva-contrasena} route under {@code tetengo.enlaces.base}: the native app's
 * {@code tetengo://app} by default, or the PWA's URL with its hash ({@code https://<pwa>/#}).
 */
@ConfigurationProperties("tetengo.enlaces")
public record PropiedadesDeEnlaces(String recuperacion) {

    public String recuperacion(String token) {
        return recuperacion.replace("{token}", token);
    }
}
