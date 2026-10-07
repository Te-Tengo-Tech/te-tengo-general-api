package tech.tetengo.api.monitoreo.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.AbrirVistaEnVivo;
import tech.tetengo.api.monitoreo.application.CerrarVistaEnVivo;
import tech.tetengo.api.monitoreo.application.SesionDeVistaEnVivo;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-23: live view sessions. Any member, invited ones too. */
@RestController
@Tag(name = "Vista en vivo", description = "Live view and its access log (US-23, US-24).")
class VistaEnVivoController {

    private static final String API = ApiVersioning.BASE;

    private final AbrirVistaEnVivo abrirVistaEnVivo;
    private final CerrarVistaEnVivo cerrarVistaEnVivo;

    VistaEnVivoController(AbrirVistaEnVivo abrirVistaEnVivo, CerrarVistaEnVivo cerrarVistaEnVivo) {
        this.abrirVistaEnVivo = abrirVistaEnVivo;
        this.cerrarVistaEnVivo = cerrarVistaEnVivo;
    }

    @Operation(
            summary = "Open the live view (US-23)",
            description =
                    "Returns {sesionId, urlTransmision, expiraEn} (CA-23.1, CA-23.2). Errors: 409 CAMARA_DESCONECTADA (CA-23.3), 409 CAMARA_EN_PAUSA with pausadaHasta (CA-23.4).")
    @PostMapping(path = API + "/camaras/{id}/vista-en-vivo", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SesionDeVistaEnVivoResponse abrir(
            @PathVariable UUID id, @RequestBody(required = false) AbrirVistaEnVivoRequest pedido) {
        SesionDeVistaEnVivo sesion =
                abrirVistaEnVivo.ejecutar(id, UsuarioActual.id(), pedido == null ? null : pedido.alertaId());
        return new SesionDeVistaEnVivoResponse(sesion.sesionId(), sesion.urlTransmision(), sesion.expiraEn());
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
