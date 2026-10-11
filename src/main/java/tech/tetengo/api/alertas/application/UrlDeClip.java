package tech.tetengo.api.alertas.application;

import java.net.URI;
import java.time.Instant;

/**
 * The clip's pre-signed URL and, US-36, its body landmarks' URL (same expiry), or why there are none.
 *
 * @param urlPuntos null when {@code puntosNoDisponibles} says why
 * @param puntosNoDisponibles {@code SIN_CONSENTIMIENTO} (the consent to store movements is not in force)
 *     or {@code SIN_ARCHIVO} (this clip has no landmarks), CA-36.2; null when {@code urlPuntos} is set
 */
public record UrlDeClip(URI url, Instant expiraEn, URI urlPuntos, String puntosNoDisponibles) {

    public UrlDeClip(URI url, Instant expiraEn) {
        this(url, expiraEn, null, null);
    }
}
