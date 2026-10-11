package tech.tetengo.api.camaras.domain.model;

/** Life of a PC link request (RFC 8628). */
public enum EstadoAutorizacion {
    /** Waiting for the owner; the PC polls ({@code authorization_pending}). */
    PENDIENTE,
    /** The owner approved it; the PC's next poll gets its tokens. */
    APROBADA,
    /** «No reconozco esta PC» ({@code access_denied}, CA-28.4). */
    RECHAZADA,
    /** The PC got its tokens: the device code cannot be used again. */
    CANJEADA
}
