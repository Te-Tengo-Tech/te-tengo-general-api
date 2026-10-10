package tech.tetengo.api.hogares.interfaces.rest;

import java.util.UUID;

/**
 * {@code GET /api/hogar}. {@code dispositivosActivos}: active push devices of the household's members;
 * 0 means nobody in the family can receive alerts.
 */
record HogarResponse(
        UUID hogarId,
        AdultoMayorResponse adultoMayor,
        String rol,
        ConsentimientoResponse consentimiento,
        long dispositivosActivos) {}
