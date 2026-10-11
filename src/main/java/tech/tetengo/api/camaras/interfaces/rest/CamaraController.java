package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.CamarasDetectadas;
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
    private final CamarasDetectadas detectadas;

    CamaraController(ListarCamaras listarCamaras, RenombrarCamara renombrarCamara, CamarasDetectadas detectadas) {
        this.listarCamaras = listarCamaras;
        this.renombrarCamara = renombrarCamara;
        this.detectadas = detectadas;
    }

    /** US-29 from the app: the owner chooses the cameras and their rooms («Elige las cámaras»). */
    @Operation(
            summary = "Assign a webcam to a room (owner, US-29)",
            description =
                    "A webcam the PC detected (in use or «sin usar») starts watching that room, or moves to it: ambienteId, or nombreAmbiente (a new name creates the room). The PC hears it on its control channel and starts capturing; later alerts use the room's name (CA-29.2). Returns the Camara. Errors: 409 AMBIENTE_OCUPADO {ambiente, camara} (CA-29.3), 422 AMBIENTE_NO_PERMITIDO (no bathrooms), 422 AMBIENTE_NOMBRE_VACIO, 422 AMBIENTE_NOMBRE_MUY_LARGO, 404 AMBIENTE_NO_ENCONTRADO, 404 CAMARA_NO_ENCONTRADA, 403 SOLO_TITULAR.")
    @PutMapping(path = "/{id}/ambiente", version = ApiVersioning.V1)
    CamaraResponse asignar(@PathVariable UUID id, @Valid @RequestBody AmbienteDeCamaraRequest pedido) {
        UsuarioActual.exigirTitular();
        return CamaraMapper.aRespuesta(
                detectadas.asignar(id, pedido.ambienteId(), pedido.nombreAmbiente(), UsuarioActual.id()));
    }

    @Operation(
            summary = "Stop using a camera: «Dejar de usar» (owner, US-29)",
            description =
                    "The camera leaves its room and stops capturing; it stays among the PC's webcams «sin usar». Errors: 404 CAMARA_NO_ENCONTRADA, 403 SOLO_TITULAR.")
    @DeleteMapping(path = "/{id}/ambiente", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dejarDeUsar(@PathVariable UUID id) {
        UsuarioActual.exigirTitular();
        detectadas.dejarDeUsar(id);
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
                    "The camera's room by name (nombreHabitacion) or by ambienteId (US-29); later alerts use the new name (CA-06.2). Errors: 422 CAMARA_NOMBRE_VACIO (CA-06.3), 422 CAMARA_NOMBRE_MUY_LARGO, 409 AMBIENTE_OCUPADO (CA-29.3), 404 AMBIENTE_NO_ENCONTRADO, 404 CAMARA_NO_ENCONTRADA, 403 SOLO_TITULAR.")
    @PatchMapping(path = "/{id}", version = ApiVersioning.V1)
    CamaraResponse renombrar(@PathVariable UUID id, @Valid @RequestBody RenombrarCamaraRequest pedido) {
        UsuarioActual.exigirTitular();
        return CamaraMapper.aRespuesta(
                renombrarCamara.ejecutar(id, pedido.nombreHabitacion(), pedido.ambienteId(), UsuarioActual.id()));
    }
}
