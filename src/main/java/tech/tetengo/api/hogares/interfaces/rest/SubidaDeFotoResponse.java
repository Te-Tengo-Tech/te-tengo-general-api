package tech.tetengo.api.hogares.interfaces.rest;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** API contract §13: {@code PUT url} with exactly these {@code cabeceras} before {@code expiraEn}. */
record SubidaDeFotoResponse(UUID subidaId, URI url, Map<String, String> cabeceras, Instant expiraEn) {}
