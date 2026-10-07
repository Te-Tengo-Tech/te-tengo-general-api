package tech.tetengo.api.alertas.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CA-16.4 retries. How often and how many times are not in the backlog: implementation choices in
 * {@code application.yml}.
 */
@ConfigurationProperties("tetengo.push")
public record PropiedadesDePush(Duration reintentoCada, int intentosMaximos) {}
