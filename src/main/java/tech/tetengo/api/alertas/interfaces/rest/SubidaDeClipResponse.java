package tech.tetengo.api.alertas.interfaces.rest;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

record SubidaDeClipResponse(URI urlSubida, Map<String, String> cabeceras, Instant expiraEn) {}
