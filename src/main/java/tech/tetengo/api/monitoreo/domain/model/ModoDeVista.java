package tech.tetengo.api.monitoreo.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * What the household agent draws on the frames it publishes (API contract §4). The mode applies to the
 * camera's stream, so every viewer sees the same one (implementation choice).
 */
public enum ModoDeVista {
    /** The camera frame. */
    VIDEO,
    /** The camera frame with the skeleton drawn on top. */
    VIDEO_CON_POSTURA,
    /** The skeleton on a plain background: no camera pixels leave the household PC. */
    SOLO_POSTURA;

    public static final ModoDeVista PREDETERMINADO = VIDEO;

    public static Optional<ModoDeVista> desde(String valor) {
        return Arrays.stream(values()).filter(m -> m.name().equals(valor)).findFirst();
    }
}
