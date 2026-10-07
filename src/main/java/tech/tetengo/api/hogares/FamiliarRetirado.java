package tech.tetengo.api.hogares;

import java.util.UUID;

/** US-08 / CA-08.3: the owner removed a family member, who no longer receives alerts. */
public record FamiliarRetirado(UUID hogarId, UUID usuarioId) {}
