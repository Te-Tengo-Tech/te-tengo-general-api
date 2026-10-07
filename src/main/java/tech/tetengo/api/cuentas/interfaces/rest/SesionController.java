package tech.tetengo.api.cuentas.interfaces.rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.cuentas.application.CerrarSesion;
import tech.tetengo.api.cuentas.application.IniciarSesion;
import tech.tetengo.api.cuentas.application.RefrescarSesion;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
@RequestMapping(ApiVersioning.BASE + "/sesiones")
class SesionController {

    private final IniciarSesion iniciarSesion;
    private final RefrescarSesion refrescarSesion;
    private final CerrarSesion cerrarSesion;

    SesionController(IniciarSesion iniciarSesion, RefrescarSesion refrescarSesion, CerrarSesion cerrarSesion) {
        this.iniciarSesion = iniciarSesion;
        this.refrescarSesion = refrescarSesion;
        this.cerrarSesion = cerrarSesion;
    }

    @PostMapping(version = ApiVersioning.V1)
    Sesion iniciar(@Valid @RequestBody IniciarSesionRequest pedido) {
        return iniciarSesion.ejecutar(pedido.correo(), pedido.contrasena());
    }

    @PostMapping(path = "/refresco", version = ApiVersioning.V1)
    Sesion refrescar(@Valid @RequestBody RefrescarSesionRequest pedido) {
        return refrescarSesion.ejecutar(pedido.tokenRefresco());
    }

    @DeleteMapping(path = "/actual", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrar() {
        UsuarioActual.sesionId().ifPresent(sesion -> cerrarSesion.ejecutar(UsuarioActual.id(), sesion));
    }
}
