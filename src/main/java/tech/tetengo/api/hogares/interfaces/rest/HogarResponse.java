package tech.tetengo.api.hogares.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/hogar}. {@code dispositivosActivos}: active push devices of the household's members;
 * 0 means nobody in the family can receive alerts. {@code eliminacion}: the deletion of the
 * recordings after the latest revocation, null when the consent was never revoked.
 */
record HogarResponse(
        UUID hogarId,
        AdultoMayorResponse adultoMayor,
        String rol,
        ConsentimientoResponse consentimiento,
        long dispositivosActivos,
        EliminacionResponse eliminacion) {

    /** {@code estado}: {@code PROGRAMADA} or {@code TERMINADA}; {@code terminadaEn} null until then. */
    record EliminacionResponse(String estado, long clips, Instant programadaEn, Instant terminadaEn) {}
}
