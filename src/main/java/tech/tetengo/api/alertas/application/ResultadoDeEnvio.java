package tech.tetengo.api.alertas.application;

/** Outcome of a push notice. */
public enum ResultadoDeEnvio {
    /** The push service accepted it for at least one device. */
    ENTREGADO,
    /** Nobody to send it to: no recipient has a registered device. */
    SIN_DISPOSITIVOS,
    /** The push service failed; the notice is queued for retry (CA-16.4). */
    PENDIENTE;

    /** Nothing left to do: delivered or nobody to deliver to. */
    public boolean resuelto() {
        return this != PENDIENTE;
    }
}
