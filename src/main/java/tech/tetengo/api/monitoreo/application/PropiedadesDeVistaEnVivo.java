package tech.tetengo.api.monitoreo.application;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live view through MediaMTX (API contract §4, ADR 0007).
 *
 * @param urlPublicacion where the agent publishes; {@code {camaraId}} is replaced
 * @param urlHls base of the app's {@code urlTransmision}
 * @param mediamtxApi MediaMTX's control API, never exposed outside the server
 * @param secretoAutorizacion shared secret of MediaMTX's authorization requests; blank rejects them all
 * @param inactividad a session ends when its viewer has not read for this long (implementation choice)
 * @param duracionMaxima maximum length of a session (implementation choice)
 */
@ConfigurationProperties("tetengo.vista-en-vivo")
public record PropiedadesDeVistaEnVivo(
        String urlPublicacion,
        String urlHls,
        String mediamtxApi,
        String secretoAutorizacion,
        Duration inactividad,
        Duration duracionMaxima) {

    public PropiedadesDeVistaEnVivo {
        urlPublicacion = urlPublicacion == null ? "rtsp://localhost:8554/camaras/{camaraId}" : urlPublicacion;
        urlHls = urlHls == null ? "http://localhost:8888" : urlHls;
        inactividad = inactividad == null ? Duration.ofSeconds(30) : inactividad;
        duracionMaxima = duracionMaxima == null ? Duration.ofMinutes(10) : duracionMaxima;
    }

    /** The agent's publish URL for the camera. */
    public URI urlPublicacionDe(UUID camaraId) {
        return URI.create(urlPublicacion.replace("{camaraId}", camaraId.toString()));
    }

    /** {@code <HLS base>/camaras/<camaraId>/index.m3u8?token=<viewer token>}. */
    public URI urlTransmisionDe(UUID camaraId, String token) {
        String base = urlHls.endsWith("/") ? urlHls.substring(0, urlHls.length() - 1) : urlHls;
        return URI.create("%s/camaras/%s/index.m3u8?token=%s"
                .formatted(base, camaraId, URLEncoder.encode(token, StandardCharsets.UTF_8)));
    }
}
