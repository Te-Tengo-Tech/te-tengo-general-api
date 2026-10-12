package tech.tetengo.api.camaras.application;

import java.util.UUID;

/**
 * The token response (RFC 6749 §5.1) of a PC: a short-lived access token and the next refresh token,
 * which rotates on every use (RFC 9700 §2.2.2).
 */
public record TokensDePc(String tokenAcceso, long expiraEnSegundos, String tokenRenovacion, UUID pcId, UUID hogarId) {}
