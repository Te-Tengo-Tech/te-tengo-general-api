package tech.tetengo.api.shared.infrastructure.push;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Names a push token in the logs without writing it: the first 12 hex characters of its SHA-256. A
 * token is a credential to message a phone, so it is never logged.
 */
final class HuellaDeToken {

    private HuellaDeToken() {}

    static String de(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
