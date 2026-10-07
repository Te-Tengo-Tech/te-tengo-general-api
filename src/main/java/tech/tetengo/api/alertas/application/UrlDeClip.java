package tech.tetengo.api.alertas.application;

import java.net.URI;
import java.time.Instant;

public record UrlDeClip(URI url, Instant expiraEn) {}
