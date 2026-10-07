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
import tech.tetengo.api.cuentas.application.RegistrarCuenta;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
@RequestMapping(ApiVersioning.BASE + "/cuentas")
@Tag(name = "Cuentas", description = "Accounts of family members and caregivers (US-01).")
class CuentaController {

    private final RegistrarCuenta registrarCuenta;

    CuentaController(RegistrarCuenta registrarCuenta) {
        this.registrarCuenta = registrarCuenta;
    }

    @Operation(
            summary = "Register an account (US-01)",
            description =
                    "201 with the account. Errors: 409 CORREO_EN_USO (CA-01.2), 400 VALIDACION with campos (CA-01.3).")
    @SecurityRequirements
    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    CuentaResponse registrar(@Valid @RequestBody RegistrarCuentaRequest pedido) {
        return CuentaMapper.aRespuesta(registrarCuenta.ejecutar(pedido.correo(), pedido.contrasena(), pedido.nombre()));
    }
}
