package tech.tetengo.api.monitoreo.infrastructure.mediamtx;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tech.tetengo.api.monitoreo.application.PropiedadesDeVistaEnVivo;
import tech.tetengo.api.monitoreo.application.port.ServicioDeTransmision;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.security.Secretos;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MediaMTX's control API ({@code /v3}, ADR 0007), reachable only from the server
 * ({@code TT_VIVO_MEDIAMTX_API}). With no address configured it does nothing.
 */
@Component
class ServicioDeTransmisionMediaMtx implements ServicioDeTransmision {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeTransmisionMediaMtx.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PREFIJO_DE_CAMARA = "camaras/";

    /** Kick endpoint of each kind of MediaMTX session that can publish or read a path. */
    private static final Map<String, String> EXPULSION = Map.of(
            "hlsSession", "/v3/hls/sessions/kick/{id}",
            "rtspSession", "/v3/rtsp/sessions/kick/{id}",
            "rtspsSession", "/v3/rtsps/sessions/kick/{id}",
            "rtmpConn", "/v3/rtmp/conns/kick/{id}",
            "rtmpsConn", "/v3/rtmps/conns/kick/{id}",
            "webRTCSession", "/v3/webrtc/sessions/kick/{id}",
            "srtConn", "/v3/srt/conns/kick/{id}");

    private final RestClient api;

    ServicioDeTransmisionMediaMtx(PropiedadesDeVistaEnVivo propiedades) {
        String base = propiedades.mediamtxApi();
        if (base == null || base.isBlank()) {
            this.api = null;
            return;
        }
        var fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.api = RestClient.builder().baseUrl(base).requestFactory(fabrica).build();
    }

    /** Lists of the reader sessions the app can open: LL-HLS and WebRTC (WHEP). */
    private static final Map<String, String> LISTAS_DE_LECTORES = Map.of(
            Lector.HLS, "/v3/hls/sessions/list?itemsPerPage=1000",
            Lector.WEBRTC, "/v3/webrtc/sessions/list?itemsPerPage=1000");

    @Override
    public List<Lector> lectores() {
        if (api == null) {
            return List.of();
        }
        List<Lector> lectores = new ArrayList<>();
        LISTAS_DE_LECTORES.forEach((tipo, lista) -> lectores.addAll(lectores(tipo, lista)));
        return lectores;
    }

    private List<Lector> lectores(String tipo, String uri) {
        List<Lector> lectores = new ArrayList<>();
        try {
            JsonNode lista = leer(api.get().uri(uri).retrieve().body(String.class));
            for (JsonNode sesion : lista.path("items")) {
                // A WebRTC session also lists publishers; only readers count (HLS sessions always read).
                if (Lector.WEBRTC.equals(tipo)
                        && !"read".equals(sesion.path("state").asString("read"))) {
                    continue;
                }
                camara(sesion.path("path").asString(""))
                        .ifPresent(camara -> lectores.add(new Lector(
                                sesion.path("id").asString(),
                                tipo,
                                camara,
                                PeticionDeMediaMtx.tokenDeQuery(
                                                sesion.path("query").asString(""))
                                        .map(Secretos::huella)
                                        .orElse(null),
                                sesion.path("outboundBytes").asLong(0))));
            }
        } catch (RestClientException | JacksonException e) {
            log.warn("No se pudo consultar los lectores {} de MediaMTX: {}", tipo, e.getMessage());
        }
        return lectores;
    }

    @Override
    public void expulsarCamara(UUID camaraId) {
        if (api == null) {
            return;
        }
        try {
            JsonNode ruta = leer(api.get()
                    .uri("/v3/paths/get/" + PREFIJO_DE_CAMARA + "{id}", camaraId)
                    .retrieve()
                    .body(String.class));
            JsonNode fuente = ruta.path("source");
            if (fuente.isObject()) {
                expulsar(fuente.path("type").asString(""), fuente.path("id").asString(""));
            }
            for (JsonNode lector : ruta.path("readers")) {
                expulsar(lector.path("type").asString(""), lector.path("id").asString(""));
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("La cámara {} no tiene una ruta activa en MediaMTX", camaraId);
        } catch (RestClientException | JacksonException e) {
            log.warn("No se pudo expulsar la transmisión de la cámara {} de MediaMTX: {}", camaraId, e.getMessage());
        }
    }

    @Override
    public void expulsarLectores(Collection<String> huellasDeToken) {
        if (api == null || huellasDeToken.isEmpty()) {
            return;
        }
        Set<String> huellas = new HashSet<>(huellasDeToken);
        lectores().stream()
                .filter(lector -> huellas.contains(lector.huellaToken()))
                .forEach(lector -> expulsar(lector.tipo(), lector.id()));
    }

    private void expulsar(String tipo, String id) {
        String ruta = EXPULSION.get(tipo);
        if (ruta == null || id.isBlank()) {
            return;
        }
        try {
            api.post().uri(ruta, id).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("La sesión {} {} ya no está en MediaMTX", tipo, id);
        } catch (RestClientException e) {
            log.warn("No se pudo expulsar la sesión {} {} de MediaMTX: {}", tipo, id, e.getMessage());
        }
    }

    private static Optional<UUID> camara(String ruta) {
        return new PeticionDeMediaMtx(null, null, null, ruta, null, null).camara();
    }

    private static JsonNode leer(String cuerpo) {
        return JSON.readTree(cuerpo == null ? "{}" : cuerpo);
    }
}
