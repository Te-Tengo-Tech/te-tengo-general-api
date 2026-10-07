package tech.tetengo.api.hogares.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orden de aviso", description = "Contact order and wait time (US-10).")
class OrdenDeAvisoController {

    private final ConfiguracionDeAviso configuracion;

    OrdenDeAvisoController(ConfiguracionDeAviso configuracion) {
        this.configuracion = configuracion;
    }

    @Operation(
            summary = "Read the alert routing (US-10)",
            description = "Defaults to 5 minutes (CA-10.3); with a single member there is no secondary (CA-10.4).")
    @GetMapping(version = ApiVersioning.V1)
    OrdenDeAvisoResponse consultar() {
        return aRespuesta(configuracion.consultar(UsuarioActual.id()));
    }

    @Operation(
            summary = "Set the alert routing (owner, US-10)",
            description =
                    "Primary, secondary and a wait of 3, 5 or 10 minutes (CA-10.1, CA-10.2). Errors: 422 ESPERA_INVALIDA, 422 CONTACTO_NO_ES_FAMILIAR, 403 SOLO_TITULAR.")
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
