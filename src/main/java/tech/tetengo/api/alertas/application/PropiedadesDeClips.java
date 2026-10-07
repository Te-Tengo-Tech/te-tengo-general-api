package tech.tetengo.api.alertas.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Lifetimes of the pre-signed clip URLs ("short-lived" in the API contract; implementation choice). */
@ConfigurationProperties("tetengo.clips")
public record PropiedadesDeClips(Duration vigenciaUrlSubida, Duration vigenciaUrlLectura) {}
