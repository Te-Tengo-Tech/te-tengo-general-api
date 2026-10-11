package tech.tetengo.api.alertas.interfaces.rest;

import java.net.URI;
import java.time.Instant;

/**
 * {@code {url, expiraEn, puntos, puntosNoDisponibles}}: the clip and, US-36, its body landmarks
 * ({@code puntos}, null with {@code puntosNoDisponibles} saying why).
 */
record UrlDeClipResponse(URI url, Instant expiraEn, Puntos puntos, String puntosNoDisponibles) {

    record Puntos(URI url, Instant expiraEn) {}
}
