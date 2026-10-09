package tech.tetengo.api.cuentas.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Sesiones", description = "Sign-in, refresh and sign-out (US-02).")
class SesionController {

    private final IniciarSesion iniciarSesion;
    private final RefrescarSesion refrescarSesion;
    private final CerrarSesion cerrarSesion;

    SesionController(IniciarSesion iniciarSesion, RefrescarSesion refrescarSesion, CerrarSesion cerrarSesion) {
        this.iniciarSesion = iniciarSesion;
        this.refrescarSesion = refrescarSesion;
        this.cerrarSesion = cerrarSesion;
    }

    @Operation(
            summary = "Sign in (US-02)",
            description =
                    "Returns a Sesion. Errors: 401 CREDENCIALES_INVALIDAS (CA-02.2); 423 CUENTA_BLOQUEADA with bloqueadaHasta after 5 consecutive failures (CA-02.3).")
    @SecurityRequirements
    @PostMapping(version = ApiVersioning.V1)
    Sesion iniciar(@Valid @RequestBody IniciarSesionRequest pedido) {
        return iniciarSesion.ejecutar(pedido.correo(), pedido.contrasena());
    }

    @Operation(
            summary = "Refresh the session (US-02)",
            description =
                    "Trades the refresh token for new tokens; the refresh token rotates. Errors: 401 SESION_EXPIRADA.")
    @SecurityRequirements
    @PostMapping(path = "/refresco", version = ApiVersioning.V1)
    Sesion refrescar(@Valid @RequestBody RefrescarSesionRequest pedido) {
        return refrescarSesion.ejecutar(pedido.tokenRefresco());
    }

    @Operation(
            summary = "Sign out (US-02)",
            description = "Revokes the refresh token; a new sign-in is required (CA-02.4).")
    @DeleteMapping(path = "/actual", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrar() {
        UsuarioActual.sesionId().ifPresent(sesion -> cerrarSesion.ejecutar(UsuarioActual.id(), sesion));
    }
}
