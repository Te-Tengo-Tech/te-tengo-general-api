package tech.tetengo.api.monitoreo.interfaces.rest;

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
class VistaEnVivoController {

    private static final String API = ApiVersioning.BASE;

    private final AbrirVistaEnVivo abrirVistaEnVivo;
    private final CerrarVistaEnVivo cerrarVistaEnVivo;

    VistaEnVivoController(AbrirVistaEnVivo abrirVistaEnVivo, CerrarVistaEnVivo cerrarVistaEnVivo) {
        this.abrirVistaEnVivo = abrirVistaEnVivo;
        this.cerrarVistaEnVivo = cerrarVistaEnVivo;
    }

    @PostMapping(path = API + "/camaras/{id}/vista-en-vivo", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SesionDeVistaEnVivoResponse abrir(
            @PathVariable UUID id, @RequestBody(required = false) AbrirVistaEnVivoRequest pedido) {
        SesionDeVistaEnVivo sesion =
                abrirVistaEnVivo.ejecutar(id, UsuarioActual.id(), pedido == null ? null : pedido.alertaId());
        return new SesionDeVistaEnVivoResponse(sesion.sesionId(), sesion.urlTransmision(), sesion.expiraEn());
    }

    @DeleteMapping(path = API + "/vista-en-vivo/{sesionId}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrar(@PathVariable UUID sesionId) {
        cerrarVistaEnVivo.ejecutar(sesionId, UsuarioActual.id());
    }
}
