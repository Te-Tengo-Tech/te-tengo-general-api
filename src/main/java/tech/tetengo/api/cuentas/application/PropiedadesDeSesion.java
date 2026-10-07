package tech.tetengo.api.cuentas.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token lifetimes. The API contract marks them as an implementation choice: access token 60 min,
 * refresh token 30 days (see {@code application.yml}).
 */
@ConfigurationProperties("tetengo.sesiones")
public record PropiedadesDeSesion(Duration vigenciaAcceso, Duration vigenciaRefresco) {}
