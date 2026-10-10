package tech.tetengo.api.alertas.domain.model;

/**
 * Whether the family got the alert's push notice (CA-16.1, CA-16.4). The app shows the alert either
 * way; this tells it whether the notice reached a phone and whether the API is still trying.
 */
public enum EstadoAviso {
    /** The alert was just created; the notice goes out once the agent's event is saved. */
    ENVIANDO,
    /** The push service accepted the notice for at least one device of the family. */
    ENTREGADO,
    /**
     * Not delivered yet: the push service failed, or no member has an active device. The API keeps
     * trying, so a phone that registers again still gets it.
     */
    REINTENTANDO,
    /** The API stopped trying: the retry window ended (or the alert was closed) without delivery. */
    NO_ENTREGADO
}
