package tech.tetengo.api.monitoreo.interfaces.rest;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** {@code urlWebrtc}: the WHEP endpoint with the viewer token, null when WebRTC playback is off. */
record SesionDeVistaEnVivoResponse(UUID sesionId, URI urlTransmision, URI urlWebrtc, Instant expiraEn, String modo) {}
