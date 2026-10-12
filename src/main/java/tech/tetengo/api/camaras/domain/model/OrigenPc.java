package tech.tetengo.api.camaras.domain.model;

/** How the PC joined the household. */
public enum OrigenPc {
    /** Linked by the owner from the app with the PC's code (US-28, RFC 8628 device grant). */
    VINCULACION,
    /**
     * Installed by the project team with an installation credential (CA-06.1), before PCs could be
     * linked; created by migration {@code V24} or by the first registration of such an agent.
     */
    INSTALACION
}
