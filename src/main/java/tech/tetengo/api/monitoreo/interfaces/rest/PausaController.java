package tech.tetengo.api.monitoreo.interfaces.rest;

import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.PausarCamara;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-22: camera pauses. Any member, invited ones too, can pause and resume. */
@RestController
@RequestMapping(ApiVersioning.BASE + "/camaras/{id}/pausa")
class PausaController {

    private final PausarCamara pausarCamara;

    PausaController(PausarCamara pausarCamara) {
        this.pausarCamara = pausarCamara;
    }

    @PostMapping(version = ApiVersioning.V1)
    CamaraResponse pausar(@PathVariable UUID id, @RequestBody(required = false) PausarCamaraRequest pedido) {
        return CamaraResponse.de(pausarCamara.pausar(id, pedido == null ? null : pedido.duracion()));
    }

    @DeleteMapping(version = ApiVersioning.V1)
    CamaraResponse reanudar(@PathVariable UUID id) {
        return CamaraResponse.de(pausarCamara.reanudar(id));
    }
}
