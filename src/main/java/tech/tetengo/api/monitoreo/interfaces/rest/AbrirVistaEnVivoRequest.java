package tech.tetengo.api.monitoreo.interfaces.rest;

import java.util.UUID;

/**
 * {@code alertaId} when the live view is opened from an alert of that camera (CA-23.2); {@code modo}
 * optional: {@code VIDEO}, {@code VIDEO_CON_POSTURA} or {@code SOLO_POSTURA}; {@code mosaicoId} when it
 * is one of the cameras of a «Ver todas» opening of this member (US-41).
 */
record AbrirVistaEnVivoRequest(UUID alertaId, String modo, UUID mosaicoId) {}
