package tech.tetengo.api.shared.infrastructure.correo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/**
 * Chooses the e-mail adapter with {@code tetengo.correo.proveedor}: {@code registro} (default, only
 * logs), {@code ses} (Amazon SES) or {@code smtp} (any SMTP relay, {@code spring.mail.*}). Any other
 * value leaves no adapter, so the API does not start.
 *
 * <p>Spring Boot creates the {@link JavaMailSender} only when {@code spring.mail.host} is set, so
 * the other providers need no mail configuration; {@code smtp} without it does not start.
 */
@Configuration(proxyBeanMethods = false)
class NotificadorCorreoConfig {

    private static final String PREFIJO = "tetengo.correo";
    private static final String PROVEEDOR = "proveedor";

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "registro", matchIfMissing = true)
    NotificadorCorreo notificadorCorreoEnRegistro() {
        return new NotificadorCorreoEnRegistro();
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "ses")
    NotificadorCorreo notificadorCorreoEnSes(PropiedadesDeSes propiedades) {
        return NotificadorCorreoEnSes.crear(propiedades);
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "smtp")
    NotificadorCorreo notificadorCorreoEnSmtp(ObjectProvider<JavaMailSender> correo, PropiedadesDeSmtp propiedades) {
        JavaMailSender enviador = correo.getIfAvailable(() -> {
            throw new IllegalStateException("spring.mail.host (SPRING_MAIL_HOST) es obligatorio con SMTP");
        });
        return new NotificadorCorreoEnSmtp(enviador, propiedades.remitente());
    }
}
