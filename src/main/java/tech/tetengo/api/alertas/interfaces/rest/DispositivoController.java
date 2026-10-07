package tech.tetengo.api.alertas.interfaces.rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.application.RegistrarDispositivo;
import tech.tetengo.api.alertas.domain.model.Plataforma;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** Push device registration (API contract §7). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/dispositivos")
class DispositivoController {

    private final RegistrarDispositivo registrarDispositivo;

    DispositivoController(RegistrarDispositivo registrarDispositivo) {
        this.registrarDispositivo = registrarDispositivo;
    }

    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    void registrar(@Valid @RequestBody RegistrarDispositivoRequest pedido) {
        registrarDispositivo.ejecutar(UsuarioActual.id(), pedido.tokenPush(), Plataforma.valueOf(pedido.plataforma()));
    }

    @DeleteMapping(path = "/{tokenPush}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable String tokenPush) {
        registrarDispositivo.eliminar(UsuarioActual.id(), tokenPush);
    }
}
