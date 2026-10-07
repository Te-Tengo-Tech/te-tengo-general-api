package tech.tetengo.api.alertas.interfaces.rest;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.application.ObtenerClip;
import tech.tetengo.api.alertas.application.PrepararSubidaDeClip;
import tech.tetengo.api.alertas.application.UrlDeClip;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-18: clips of the events, uploaded by the agent and watched in the app. */
@RestController
class ClipController {

    private static final String API = ApiVersioning.BASE;

    private final PrepararSubidaDeClip prepararSubida;
    private final ObtenerClip obtenerClip;

    ClipController(PrepararSubidaDeClip prepararSubida, ObtenerClip obtenerClip) {
        this.prepararSubida = prepararSubida;
        this.obtenerClip = obtenerClip;
    }

    /** Household agent: pre-signed PUT URL for the clip of the event. */
    @PostMapping(path = API + "/agente/eventos/{eventoId}/clip", version = ApiVersioning.V1)
    UrlDeClipResponse subir(@PathVariable UUID eventoId) {
        return aRespuesta(prepararSubida.ejecutar(UsuarioActual.camaraId().orElseThrow(), eventoId));
    }

    @GetMapping(path = API + "/alertas/{id}/clip", version = ApiVersioning.V1)
    UrlDeClipResponse ver(@PathVariable UUID id) {
        return aRespuesta(obtenerClip.ejecutar(id, false));
    }

    private static UrlDeClipResponse aRespuesta(UrlDeClip url) {
        return new UrlDeClipResponse(url.url(), url.expiraEn());
    }
}
