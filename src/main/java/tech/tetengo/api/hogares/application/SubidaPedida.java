package tech.tetengo.api.hogares.application;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** API contract §13: the pre-signed PUT of a photo upload. */
public record SubidaPedida(UUID subidaId, URI url, Map<String, String> cabeceras, Instant expiraEn) {}
