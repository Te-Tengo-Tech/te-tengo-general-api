package tech.tetengo.api.alertas.interfaces.rest;

import java.net.URI;
import java.time.Instant;

record UrlDeClipResponse(URI url, Instant expiraEn) {}
