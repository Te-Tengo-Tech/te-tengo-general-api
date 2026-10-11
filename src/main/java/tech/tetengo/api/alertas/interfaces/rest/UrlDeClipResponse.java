package tech.tetengo.api.alertas.interfaces.rest;

import java.net.URI;
import java.time.Instant;

/**
 * {@code {url, expiraEn, puntos, puntosNoDisponibles}}: the clip and, US-36, its body landmarks
 * ({@code puntos}, null with {@code puntosNoDisponibles} saying why), and in {@code puntos.ligero} the
 * same landmarks in the light replay format (ADR 0011; null when not uploaded).
 */
record UrlDeClipResponse(URI url, Instant expiraEn, Puntos puntos, String puntosNoDisponibles) {

    record Puntos(URI url, Instant expiraEn, Ligero ligero) {}

    record Ligero(URI url) {}
}
