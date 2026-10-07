package tech.tetengo.api.cuentas.interfaces.rest;

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
class CuentaController {

    private final RegistrarCuenta registrarCuenta;

    CuentaController(RegistrarCuenta registrarCuenta) {
        this.registrarCuenta = registrarCuenta;
    }

    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    CuentaResponse registrar(@Valid @RequestBody RegistrarCuentaRequest pedido) {
        return CuentaMapper.aRespuesta(registrarCuenta.ejecutar(pedido.correo(), pedido.contrasena(), pedido.nombre()));
    }
}
