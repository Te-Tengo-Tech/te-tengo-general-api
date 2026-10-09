package tech.tetengo.api.monitoreo.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.AbrirVistaEnVivo;
import tech.tetengo.api.monitoreo.application.CambiarModoDeVistaEnVivo;
import tech.tetengo.api.monitoreo.application.CerrarVistaEnVivo;
import tech.tetengo.api.monitoreo.application.PrepararVistaEnVivo;
import tech.tetengo.api.monitoreo.application.SesionDeVistaEnVivo;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-23: live view sessions. Any member, invited ones too. */
@RestController
@Tag(name = "Vista en vivo", description = "Live view and its access log (US-23, US-24).")
class VistaEnVivoController {

    private static final String API = ApiVersioning.BASE;

    private final AbrirVistaEnVivo abrirVistaEnVivo;
    private final CambiarModoDeVistaEnVivo cambiarModo;
    private final CerrarVistaEnVivo cerrarVistaEnVivo;
    private final PrepararVistaEnVivo prepararVistaEnVivo;

    VistaEnVivoController(
            AbrirVistaEnVivo abrirVistaEnVivo,
            CambiarModoDeVistaEnVivo cambiarModo,
            CerrarVistaEnVivo cerrarVistaEnVivo,
            PrepararVistaEnVivo prepararVistaEnVivo) {
        this.abrirVistaEnVivo = abrirVistaEnVivo;
        this.cambiarModo = cambiarModo;
        this.cerrarVistaEnVivo = cerrarVistaEnVivo;
        this.prepararVistaEnVivo = prepararVistaEnVivo;
    }

    @Operation(
            summary = "Prepare the live view of a camera",
            description =
                    "Body {camaraId}. The camera screen opened: the camera's agent warms up (nothing is published, no session is recorded), so a live view opened next starts sooner. 204. Errors: 400 VALIDACION (campos.camaraId), 404 CAMARA_NO_ENCONTRADA, 409 CAMARA_DESCONECTADA, 409 CAMARA_EN_PAUSA with pausadaHasta, 409 SIN_CONSENTIMIENTO.")
    @PostMapping(path = API + "/vista-en-vivo/preparar", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void preparar(@RequestBody(required = false) PrepararVistaEnVivoRequest pedido) {
        prepararVistaEnVivo.ejecutar(pedido == null ? null : pedido.camaraId());
    }

    @Operation(
            summary = "Open the live view (US-23)",
            description =
                    "Body {alertaId|null, modo?}. Returns {sesionId, urlTransmision, urlWebrtc, expiraEn, modo}: urlTransmision is the camera's LL-HLS playlist on MediaMTX with the session's viewer token (CA-23.1, CA-23.2); urlWebrtc is the camera's WebRTC (WHEP) endpoint with the same token, or null when WebRTC playback is off. Errors: 400 VALIDACION (alertaId of another camera, unknown modo), 409 CAMARA_DESCONECTADA (CA-23.3), 409 CAMARA_EN_PAUSA with pausadaHasta (CA-23.4), 409 SIN_CONSENTIMIENTO.")
    @PostMapping(path = API + "/camaras/{id}/vista-en-vivo", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SesionDeVistaEnVivoResponse abrir(
            @PathVariable UUID id, @RequestBody(required = false) AbrirVistaEnVivoRequest pedido) {
        SesionDeVistaEnVivo sesion = abrirVistaEnVivo.ejecutar(
                id,
                UsuarioActual.id(),
                pedido == null ? null : pedido.alertaId(),
                pedido == null ? null : pedido.modo());
        return new SesionDeVistaEnVivoResponse(
                sesion.sesionId(),
                sesion.urlTransmision(),
                sesion.urlWebrtc(),
                sesion.expiraEn(),
                sesion.modo().name());
    }

    @Operation(
            summary = "Change the live view mode",
            description =
                    "Body {modo}: VIDEO, VIDEO_CON_POSTURA or SOLO_POSTURA; applies to the camera's stream. Returns {sesionId, modo}. Errors: 400 VALIDACION, 404 SESION_NO_ENCONTRADA.")
    @PatchMapping(path = API + "/vista-en-vivo/{sesionId}", version = ApiVersioning.V1)
    ModoDeSesionResponse cambiarModo(
            @PathVariable UUID sesionId, @RequestBody(required = false) CambiarModoRequest pedido) {
        return new ModoDeSesionResponse(
                sesionId,
                cambiarModo
                        .ejecutar(sesionId, UsuarioActual.id(), pedido == null ? null : pedido.modo())
                        .name());
    }

    @Operation(
            summary = "Close the live view (US-24)",
            description = "Records who watched, when and for how long (CA-24.1).")
    @DeleteMapping(path = API + "/vista-en-vivo/{sesionId}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrar(@PathVariable UUID sesionId) {
        cerrarVistaEnVivo.ejecutar(sesionId, UsuarioActual.id());
    }
}
