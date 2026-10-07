package tech.tetengo.api.monitoreo.interfaces.rest;

import java.util.UUID;

/** {@code alertaId} when the live view is opened from an alert (CA-23.2). */
record AbrirVistaEnVivoRequest(UUID alertaId) {}
