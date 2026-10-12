package tech.tetengo.api.cuentas.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.cuentas.application.ActualizarPerfil;
import tech.tetengo.api.cuentas.application.CambiarContrasena;
import tech.tetengo.api.cuentas.application.ConsultarPerfil;
import tech.tetengo.api.cuentas.application.Perfil;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** API contract §13: the caller's own profile and password («Tu perfil»). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/cuenta")
@Tag(name = "Perfil", description = "The caller's own profile, photo and password (API contract §13).")
class PerfilController {

    private final ConsultarPerfil consultarPerfil;
    private final ActualizarPerfil actualizarPerfil;
    private final CambiarContrasena cambiarContrasena;

    PerfilController(
            ConsultarPerfil consultarPerfil, ActualizarPerfil actualizarPerfil, CambiarContrasena cambiarContrasena) {
        this.consultarPerfil = consultarPerfil;
        this.actualizarPerfil = actualizarPerfil;
        this.cambiarContrasena = cambiarContrasena;
    }

    @Operation(summary = "Read the caller's profile", description = "Name, e-mail, phone, relationship and photo.")
    @GetMapping(version = ApiVersioning.V1)
    PerfilResponse consultar() {
        return aRespuesta(consultarPerfil.ejecutar(UsuarioActual.id()));
    }

    @Operation(
            summary = "Edit the caller's profile",
            description =
                    "Replaces nombre, telefono and parentesco; a blank or omitted telefono or parentesco clears it. Errors: 400 VALIDACION.")
    @PutMapping(version = ApiVersioning.V1)
    PerfilResponse actualizar(@Valid @RequestBody ActualizarPerfilRequest pedido) {
        return aRespuesta(
                actualizarPerfil.ejecutar(UsuarioActual.id(), pedido.nombre(), pedido.telefono(), pedido.parentesco()));
    }

    @Operation(
            summary = "Change the caller's password",
            description =
                    "Other sessions stay open. Errors: 422 CONTRASENA_ACTUAL_INCORRECTA, 400 VALIDACION (nueva: 8 characters or more, with a number).")
    @PutMapping(path = "/contrasena", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest pedido) {
        cambiarContrasena.ejecutar(UsuarioActual.id(), pedido.actual(), pedido.nueva());
    }

    private static PerfilResponse aRespuesta(Perfil perfil) {
        var c = perfil.cuenta();
        return new PerfilResponse(
                c.getId(), c.getNombre(), c.getCorreo(), c.getTelefono(), c.getParentesco(), perfil.foto());
    }
}
