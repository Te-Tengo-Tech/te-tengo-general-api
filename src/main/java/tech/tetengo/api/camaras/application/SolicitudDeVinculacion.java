package tech.tetengo.api.camaras.application;

import java.net.URI;

/**
 * The device authorization response (RFC 8628 §3.2): the secret device code the PC keeps, the code it
 * shows ({@code XXXX-XXXX}), the page and QR to approve it, how long it lasts and how often to poll.
 */
public record SolicitudDeVinculacion(
        String codigoDispositivo,
        String codigoUsuario,
        URI uriVerificacion,
        URI uriVerificacionCompleta,
        long expiraEnSegundos,
        long intervaloSegundos) {}
