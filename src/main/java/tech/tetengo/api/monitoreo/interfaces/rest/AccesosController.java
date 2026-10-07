package tech.tetengo.api.monitoreo.interfaces.rest;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.ConsultarAccesos;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-24: live view access log. */
@RestController
class AccesosController {

    private final ConsultarAccesos consultarAccesos;

    AccesosController(ConsultarAccesos consultarAccesos) {
        this.consultarAccesos = consultarAccesos;
    }

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
