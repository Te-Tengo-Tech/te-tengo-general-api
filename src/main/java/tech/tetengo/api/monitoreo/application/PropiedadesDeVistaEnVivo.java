package tech.tetengo.api.monitoreo.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live view stream (API contract §4, proposal pending team confirmation): the base of the
 * {@code wss://} URL and how long the URL can be used to connect (implementation choice).
 */
@ConfigurationProperties("tetengo.vista-en-vivo")
public record PropiedadesDeVistaEnVivo(String urlBase, Duration vigencia) {}
