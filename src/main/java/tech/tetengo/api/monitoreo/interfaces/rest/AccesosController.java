package tech.tetengo.api.monitoreo.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.ConsultarAccesos;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-24: live view access log. */
@RestController
@Tag(name = "Vista en vivo", description = "Live view and its access log (US-23, US-24).")
class AccesosController {

    private final ConsultarAccesos consultarAccesos;

    AccesosController(ConsultarAccesos consultarAccesos) {
        this.consultarAccesos = consultarAccesos;
    }

    @Operation(
            summary = "Live view access log (US-24)",
            description = "Newest first (CA-24.2); empty when nobody watched (CA-24.3).")
    @GetMapping(path = ApiVersioning.BASE + "/accesos-vista-en-vivo", version = ApiVersioning.V1)
    List<AccesoResponse> listar() {
        return consultarAccesos.ejecutar().stream()
                .map(a -> new AccesoResponse(
                        new AccesoResponse.Usuario(a.usuarioId(), a.nombre()),
                        a.inicio(),
                        a.duracionSegundos(),
                        a.desdeAlerta()))
                .toList();
    }
}
