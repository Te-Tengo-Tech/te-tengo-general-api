package tech.tetengo.api.shared.infrastructure.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Any SMTP relay, used when {@code tetengo.correo.proveedor=smtp}. The connection (host, port,
 * credentials, STARTTLS) is Spring Boot's standard {@code spring.mail.*}, set with {@code
 * SPRING_MAIL_HOST}, {@code SPRING_MAIL_PORT}, {@code SPRING_MAIL_USERNAME} and {@code
 * SPRING_MAIL_PASSWORD}.
 *
 * @param remitente sender address the relay accepts (e.g. {@code Te Tengo
 *     <no-responder@example.com>}); required
 */
@ConfigurationProperties("tetengo.correo.smtp")
public record PropiedadesDeSmtp(String remitente) {}
