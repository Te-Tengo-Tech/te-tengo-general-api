package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.hogares.OrdenDeAviso.Configuracion;
import tech.tetengo.api.hogares.application.ConfiguracionDeAviso;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-10: contact order and wait time. */
@RestController
@RequestMapping(ApiVersioning.BASE + "/hogar/aviso")
class OrdenDeAvisoController {

    private final ConfiguracionDeAviso configuracion;

    OrdenDeAvisoController(ConfiguracionDeAviso configuracion) {
        this.configuracion = configuracion;
    }

    @GetMapping(version = ApiVersioning.V1)
    OrdenDeAvisoResponse consultar() {
        return aRespuesta(configuracion.consultar(UsuarioActual.id()));
    }

    @PutMapping(version = ApiVersioning.V1)
    OrdenDeAvisoResponse configurar(@Valid @RequestBody OrdenDeAvisoRequest pedido) {
        UsuarioActual.exigirTitular();
        return aRespuesta(configuracion.configurar(
                UsuarioActual.id(), pedido.principalId(), pedido.secundarioId(), pedido.esperaMinutos()));
    }

    private static OrdenDeAvisoResponse aRespuesta(Configuracion c) {
        return new OrdenDeAvisoResponse(c.principalId(), c.secundarioId(), c.esperaMinutos());
    }
}
