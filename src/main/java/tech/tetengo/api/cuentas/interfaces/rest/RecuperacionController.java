package tech.tetengo.api.cuentas.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.cuentas.application.ConfirmarRecuperacion;
import tech.tetengo.api.cuentas.application.SolicitarRecuperacion;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
@RequestMapping(ApiVersioning.BASE + "/recuperaciones")
@Tag(name = "Recuperación de contraseña", description = "Password recovery (US-03).")
class RecuperacionController {

    private final SolicitarRecuperacion solicitarRecuperacion;
    private final ConfirmarRecuperacion confirmarRecuperacion;

    RecuperacionController(SolicitarRecuperacion solicitarRecuperacion, ConfirmarRecuperacion confirmarRecuperacion) {
        this.solicitarRecuperacion = solicitarRecuperacion;
        this.confirmarRecuperacion = confirmarRecuperacion;
    }

    /** Always {@code 202}, whether or not the account exists (CA-03.1, CA-03.2). */
    @Operation(
            summary = "Ask for a password reset link (US-03)",
            description = "Always 202, whether or not the account exists (CA-03.1, CA-03.2).")
    @SecurityRequirements
    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.ACCEPTED)
    void solicitar(@Valid @RequestBody SolicitarRecuperacionRequest pedido) {
        solicitarRecuperacion.ejecutar(pedido.correo());
    }

    @Operation(
            summary = "Set a new password with the link (US-03)",
            description = "204. Errors: 410 ENLACE_VENCIDO, links last 30 minutes (CA-03.3).")
    @SecurityRequirements
    @PostMapping(path = "/confirmacion", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmar(@Valid @RequestBody ConfirmarRecuperacionRequest pedido) {
        confirmarRecuperacion.ejecutar(pedido.token(), pedido.nuevaContrasena());
    }
}
