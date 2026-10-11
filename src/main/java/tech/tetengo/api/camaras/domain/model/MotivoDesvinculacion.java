package tech.tetengo.api.camaras.domain.model;

/** Why a PC left the household (US-31, CA-28.7). */
public enum MotivoDesvinculacion {
    /** The owner chose «Quitar esta PC» in the app (CA-31.1, CA-31.2). */
    DESDE_LA_APP,
    /** Someone chose «Desvincular esta PC» on the PC itself (CA-31.3). */
    DESDE_LA_PC,
    /**
     * A refresh token that had already been rotated was used again (RFC 9700 §4.14.2): someone else
     * may hold the PC's credentials, so every token of the PC is revoked and it must be linked again.
     */
    REUSO_DE_TOKEN,
    /** 90 days or more without connecting (CA-28.7). */
    VENCIDA
}
