package tech.tetengo.api.alertas.application;

/** Outcome of one attempt of a push notice. */
public enum ResultadoDeEnvio {
    /** The push service accepted it for at least one device. */
    ENTREGADO,
    /** Nobody to send it to: no recipient has an active device, or the service rejected every token. */
    SIN_DISPOSITIVOS,
    /** The push service failed; the notice stays queued for retry (CA-16.4). */
    PENDIENTE
}
