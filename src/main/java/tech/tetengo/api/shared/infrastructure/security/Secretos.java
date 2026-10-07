package tech.tetengo.api.shared.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random secrets handed to clients (refresh tokens, recovery and invitation links, installation
 * credentials). Only their SHA-256 {@link #huella} is stored, so a database leak does not expose them.
 */
public final class Secretos {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Secretos() {}

    /** 256 random bits, URL-safe Base64 without padding. */
    public static String generar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex SHA-256 of the secret. */
    public static String huella(String secreto) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
