package tech.tetengo.api.monitoreo.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;

/** {@code urlWebrtc} is null when WebRTC playback is off (configuration). */
public record SesionDeVistaEnVivo(
        UUID sesionId, URI urlTransmision, URI urlWebrtc, Instant expiraEn, ModoDeVista modo) {}
