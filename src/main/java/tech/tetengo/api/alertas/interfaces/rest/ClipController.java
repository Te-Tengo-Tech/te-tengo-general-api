package tech.tetengo.api.alertas.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.application.ObtenerClip;
import tech.tetengo.api.alertas.application.PrepararSubidaDeClip;
import tech.tetengo.api.alertas.application.PuntosDeClips;
import tech.tetengo.api.alertas.application.SubidaDeClip;
import tech.tetengo.api.alertas.application.UrlDeClip;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-18: clips of the events, uploaded by the agent and watched in the app. */
@RestController
@Tag(name = "Clips", description = "Clips of the events (US-18, US-26).")
class ClipController {

    private static final String API = ApiVersioning.BASE;

    private final PrepararSubidaDeClip prepararSubida;
    private final ObtenerClip obtenerClip;
    private final PuntosDeClips puntos;

    ClipController(PrepararSubidaDeClip prepararSubida, ObtenerClip obtenerClip, PuntosDeClips puntos) {
        this.prepararSubida = prepararSubida;
        this.obtenerClip = obtenerClip;
        this.puntos = puntos;
    }

    /** Household agent: pre-signed PUT URL for the clip of the event, and the headers the PUT carries. */
    @Operation(
            summary = "Get the clip upload URL (agent, US-18)",
            description =
                    "Pre-signed PUT URL for the 6 s + 6 s clip and the headers the PUT must carry. Errors: 404 EVENTO_NO_ENCONTRADO.")
    @PostMapping(path = API + "/agente/eventos/{eventoId}/clip", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SubidaDeClipResponse subir(
            @PathVariable UUID eventoId, @Valid @RequestBody(required = false) SolicitudDeClipRequest pedido) {
        SubidaDeClip subida = prepararSubida.ejecutar(
                UsuarioActual.camaraDelAgente(), eventoId, pedido == null ? null : pedido.contentType());
        return new SubidaDeClipResponse(subida.urlSubida(), subida.cabeceras(), subida.expiraEn());
    }

    /** US-18 / US-26: watch the clip, or download it with {@code descarga=true} (CA-26.2). */
    @Operation(
            summary = "Watch or download the clip (US-18, US-26)",
            description =
                    "Short-lived pre-signed URL (CA-18.1, CA-26.1); descarga=true for a download (CA-26.2). With puntos, the URL of the clip's body landmarks (US-36) and in puntos.ligero the same landmarks in the light replay format (ADR 0011; null when not uploaded), or puntosNoDisponibles SIN_CONSENTIMIENTO / SIN_ARCHIVO (CA-36.2). Errors: 404 CLIP_NO_DISPONIBLE (CA-18.2), 410 CLIP_ELIMINADO (CA-26.3), 404 ALERTA_NO_ENCONTRADA.")
    @GetMapping(path = API + "/alertas/{id}/clip", version = ApiVersioning.V1)
    UrlDeClipResponse ver(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean descarga) {
        return aRespuesta(obtenerClip.ejecutar(id, descarga));
    }

    private static UrlDeClipResponse aRespuesta(UrlDeClip url) {
        return new UrlDeClipResponse(
                url.url(),
                url.expiraEn(),
                url.urlPuntos() == null
                        ? null
                        : new UrlDeClipResponse.Puntos(
                                url.urlPuntos(),
                                url.expiraEn(),
                                url.urlPuntosLigero() == null
                                        ? null
                                        : new UrlDeClipResponse.Ligero(url.urlPuntosLigero())),
                url.puntosNoDisponibles());
    }

    /** US-36: pre-signed PUT URL for the body landmarks of the event's clip. */
    @Operation(
            summary = "Get the upload URL of the clip's landmarks (agent, US-36)",
            description =
                    "A Parquet file next to the clip (clips/<alerta>.landmarks.parquet), only with the consent to store movements in force. Errors: 404 EVENTO_NO_ENCONTRADO, 409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS (CA-36.2).")
    @PostMapping(path = API + "/agente/eventos/{eventoId}/clip/puntos", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SubidaDeClipResponse subirPuntos(@PathVariable UUID eventoId) {
        SubidaDeClip subida = puntos.prepararSubida(UsuarioActual.camaraDelAgente(), eventoId);
        return new SubidaDeClipResponse(subida.urlSubida(), subida.cabeceras(), subida.expiraEn());
    }

    /** ADR 0011: pre-signed PUT URL for the same landmarks in the light replay format. */
    @Operation(
            summary = "Get the upload URL of the clip's light landmarks (agent, US-36)",
            description =
                    "The clip's landmarks in the light replay format (TTMOV v1, gzip; ADR 0011) at clips/<alerta>.landmarks.ttmov, the file the app draws over the clip. No confirmation: the app gets it once the storage has it. Errors: 404 EVENTO_NO_ENCONTRADO, 409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS (CA-36.2).")
    @PostMapping(path = API + "/agente/eventos/{eventoId}/clip/puntos/ligero", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SubidaDeClipResponse subirPuntosLigeros(@PathVariable UUID eventoId) {
        SubidaDeClip subida = puntos.prepararSubidaLigera(UsuarioActual.camaraDelAgente(), eventoId);
        return new SubidaDeClipResponse(subida.urlSubida(), subida.cabeceras(), subida.expiraEn());
    }
}
