package tech.tetengo.api.monitoreo.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Pausas", description = "Temporary camera pauses (US-22).")
class PausaController {

    private final PausarCamara pausarCamara;

    PausaController(PausarCamara pausarCamara) {
        this.pausarCamara = pausarCamara;
    }

    @Operation(
            summary = "Pause the camera (US-22)",
            description =
                    "duracion MIN_30, HORA_1, HORAS_2 or HASTA_MANANA (next 07:00 America/Lima); returns the Camara with pausadaHasta (CA-22.1, CA-22.2). Resumes on its own with push PAUSA_FINALIZADA (CA-22.3). Errors: 422 DURACION_INVALIDA.")
    @PostMapping(version = ApiVersioning.V1)
    CamaraResponse pausar(@PathVariable UUID id, @RequestBody(required = false) PausarCamaraRequest pedido) {
        return CamaraResponse.de(pausarCamara.pausar(id, pedido == null ? null : pedido.duracion()));
    }

    @Operation(summary = "Resume the camera now (US-22)", description = "Returns the Camara.")
    @DeleteMapping(version = ApiVersioning.V1)
    CamaraResponse reanudar(@PathVariable UUID id) {
        return CamaraResponse.de(pausarCamara.reanudar(id));
    }
}
