package tech.tetengo.api.camaras.domain.model;

/** Why a PC left the household (US-31, CA-28.7). */
public enum MotivoDesvinculacion {
    /** The owner chose «Quitar esta PC» in the app (CA-31.1, CA-31.2). */
    DESDE_LA_APP,
    /** Someone chose «Desvincular esta PC» on the PC itself (CA-31.3). */
    DESDE_LA_PC,
    /** «Restablecer esta PC» on the PC: it unlinked itself and deleted everything it kept. */
    RESTABLECIDA,
    /** The program was uninstalled from the PC, which unlinked itself first. */
    DESINSTALADA,
    /**
     * A refresh token that had already been rotated was used again (RFC 9700 §4.14.2): someone else
     * may hold the PC's credentials, so every token of the PC is revoked and it must be linked again.
     */
    REUSO_DE_TOKEN,
    /** 90 days or more without connecting (CA-28.7). */
    VENCIDA;

    /**
     * The {@code motivo} the PC sends when it unlinks itself: {@code restablecida}, {@code
     * desinstalada}, or none ({@link #DESDE_LA_PC}).
     *
     * @throws IllegalArgumentException for any other value
     */
    public static MotivoDesvinculacion desdeLaPc(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return DESDE_LA_PC;
        }
        return switch (motivo) {
            case "restablecida" -> RESTABLECIDA;
            case "desinstalada" -> DESINSTALADA;
            default -> throw new IllegalArgumentException("motivo");
        };
    }

    /** Whether someone did it on the PC itself (the activity log's actor is the PC). */
    public boolean desdeLaPropiaPc() {
        return this == DESDE_LA_PC || this == RESTABLECIDA || this == DESINSTALADA;
    }
}
