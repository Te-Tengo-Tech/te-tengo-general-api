package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.ListarCamaras;
import tech.tetengo.api.camaras.application.RenombrarCamara;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
@RequestMapping(ApiVersioning.BASE + "/camaras")
@Tag(name = "Cámaras", description = "Household cameras (US-06, US-07).")
class CamaraController {

    private final ListarCamaras listarCamaras;
    private final RenombrarCamara renombrarCamara;

    CamaraController(ListarCamaras listarCamaras, RenombrarCamara renombrarCamara) {
        this.listarCamaras = listarCamaras;
        this.renombrarCamara = renombrarCamara;
    }

    @Operation(
            summary = "List the cameras (US-06, US-07)",
            description =
                    "With room name, connection status, last signal, pause and detection reliability (CA-06.1, CA-07.1, CA-22.2).")
    @GetMapping(version = ApiVersioning.V1)
    List<CamaraResponse> listar() {
        return listarCamaras.ejecutar().stream().map(CamaraMapper::aRespuesta).toList();
    }

    /** Owner only: invited members cannot change the cameras (CA-08.4). */
    @Operation(
            summary = "Rename the room (owner, US-06)",
            description =
                    "Later alerts use the new name (CA-06.2). Errors: 422 CAMARA_NOMBRE_VACIO (CA-06.3), 422 CAMARA_NOMBRE_MUY_LARGO, 404 CAMARA_NO_ENCONTRADA, 403 SOLO_TITULAR.")
    @PatchMapping(path = "/{id}", version = ApiVersioning.V1)
    CamaraResponse renombrar(@PathVariable UUID id, @Valid @RequestBody RenombrarCamaraRequest pedido) {
        UsuarioActual.exigirTitular();
        return CamaraMapper.aRespuesta(renombrarCamara.ejecutar(id, pedido.nombreHabitacion()));
    }
}
