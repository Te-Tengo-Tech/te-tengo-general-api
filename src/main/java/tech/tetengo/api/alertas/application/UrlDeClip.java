package tech.tetengo.api.alertas.application;

import java.net.URI;
import java.time.Instant;

/**
 * The clip's pre-signed URL and, US-36, its body landmarks' URL (same expiry), or why there are none.
 *
 * @param urlPuntos null when {@code puntosNoDisponibles} says why
 * @param urlPuntosLigero the same landmarks in the light replay format (ADR 0011), same expiry; null
 *     without {@code urlPuntos} or when the storage does not have it
 * @param puntosNoDisponibles {@code SIN_CONSENTIMIENTO} (the consent to store movements is not in force)
 *     or {@code SIN_ARCHIVO} (this clip has no landmarks), CA-36.2; null when {@code urlPuntos} is set
 */
public record UrlDeClip(URI url, Instant expiraEn, URI urlPuntos, URI urlPuntosLigero, String puntosNoDisponibles) {

    public UrlDeClip(URI url, Instant expiraEn) {
        this(url, expiraEn, null, null, null);
    }
}
