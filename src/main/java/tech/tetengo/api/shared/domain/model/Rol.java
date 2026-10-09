package tech.tetengo.api.shared.domain.model;

/**
 * Role carried by the {@code rol} claim. {@code TITULAR} (owner) and {@code INVITADO} (invited family
 * member) are household roles (CA-08.4); {@code AGENTE} is the per-camera token of the household agent.
 */
public enum Rol {
    TITULAR,
    INVITADO,
    AGENTE
}
