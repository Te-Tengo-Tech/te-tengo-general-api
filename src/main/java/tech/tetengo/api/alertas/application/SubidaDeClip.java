package tech.tetengo.api.alertas.application;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** Where and how the agent uploads a clip: {@code PUT urlSubida} with {@code cabeceras}. */
public record SubidaDeClip(URI urlSubida, Map<String, String> cabeceras, Instant expiraEn) {}
