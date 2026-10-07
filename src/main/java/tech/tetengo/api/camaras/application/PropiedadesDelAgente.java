package tech.tetengo.api.camaras.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Household agent settings (implementation choices documented in {@code AGENT_CONTRACT.md}). */
@ConfigurationProperties("tetengo.agente")
public record PropiedadesDelAgente(Duration vigenciaToken) {}
