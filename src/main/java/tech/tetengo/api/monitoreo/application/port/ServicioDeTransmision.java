package tech.tetengo.api.monitoreo.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The live streaming service of the architecture ("Servicio de transmisión en vivo"): MediaMTX, through
 * its control API (ADR 0007). Failures are logged, never thrown: the API's own state is what grants
 * access, MediaMTX only enforces it sooner.
 */
public interface ServicioDeTransmision {

    /** Viewers currently reading camera paths over HLS or WebRTC. Empty when the service cannot be reached. */
    List<Lector> lectores();

    /** Disconnects the camera's publisher and every reader of its path. */
    void expulsarCamara(UUID camaraId);

    /** Disconnects the readers that use these viewer tokens (SHA-256 hashes). */
    void expulsarLectores(Collection<String> huellasDeToken);

    /**
     * @param id MediaMTX's id of the HLS or WebRTC session
     * @param tipo {@code hlsSession} or {@code webRTCSession}, MediaMTX's names (how it is kicked)
     * @param huellaToken SHA-256 of the viewer token it was opened with, or null
     * @param bytesEnviados bytes sent so far: it grows while the viewer keeps reading
     */
    record Lector(String id, String tipo, UUID camaraId, String huellaToken, long bytesEnviados) {

        public static final String HLS = "hlsSession";
        public static final String WEBRTC = "webRTCSession";
    }
}
