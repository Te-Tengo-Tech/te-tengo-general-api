package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.CamaraDeLaPc;
import tech.tetengo.api.camaras.application.CamarasDeLaPc;
import tech.tetengo.api.camaras.application.GestionarAmbientes;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-29 on the linked PC, with its access token (AGENT_CONTRACT.md, "Cameras of a linked PC"). */
@RestController
@Tag(name = "Agente del hogar", description = "Endpoints of the household agent (AGENT_CONTRACT.md).")
class CamarasDeLaPcController {

    private static final String API = ApiVersioning.BASE + "/agente";

    private final CamarasDeLaPc camaras;
    private final GestionarAmbientes ambientes;

    CamarasDeLaPcController(CamarasDeLaPc camaras, GestionarAmbientes ambientes) {
        this.camaras = camaras;
        this.ambientes = ambientes;
    }

    @Operation(
            summary = "The household's rooms (agent, US-29)",
            description =
                    "Each room with the camera in use in it, on this PC or another one, so the PC marks «Ya tiene cámara» (CA-29.3). PC token.")
    @GetMapping(path = API + "/ambientes", version = ApiVersioning.V1)
    List<AmbienteResponse> ambientes() {
        UsuarioActual.pcDelAgente();
        return ambientes.listar().stream().map(AmbienteResponse::de).toList();
    }

    @Operation(summary = "This PC's cameras in use (agent, US-29)", description = "Without tokens. PC token.")
    @GetMapping(path = API + "/pc/camaras", version = ApiVersioning.V1)
    List<CamaraDeLaPcResponse> listar() {
        return camaras.listar(UsuarioActual.pcDelAgente()).stream()
                .map(camara -> CamaraDeLaPcResponse.de(new CamaraDeLaPc(camara, null)))
                .toList();
    }

    @Operation(
            summary = "Use a webcam in a room (agent, US-29)",
            description =
                    "Upsert by dispositivoId (the OS's unique device id, CA-29.4): «Usar esta cámara» or a new room (CA-29.2). The room by ambienteId or nombreAmbiente (a new name creates it). Returns the camera with its per-camera token. Errors: 409 AMBIENTE_OCUPADO {ambiente, camara} (CA-29.3), 422 AMBIENTE_NOMBRE_VACIO, 422 AMBIENTE_NOMBRE_MUY_LARGO, 404 AMBIENTE_NO_ENCONTRADO, 400 VALIDACION.")
    @PutMapping(path = API + "/pc/camaras", version = ApiVersioning.V1)
    CamaraDeLaPcResponse usar(@Valid @RequestBody CamaraDeLaPcRequest pedido) {
        return CamaraDeLaPcResponse.de(camaras.asignar(
                UsuarioActual.pcDelAgente(),
                pedido.dispositivoId(),
                pedido.nombreDispositivo(),
                pedido.ambienteId(),
                pedido.nombreAmbiente()));
    }

    @Operation(
            summary = "Stop using a camera: «Dejar de usar» (agent, US-29)",
            description =
                    "The camera leaves its room and the app; its token stops working. Errors: 404 CAMARA_NO_ENCONTRADA.")
    @DeleteMapping(path = API + "/pc/camaras/{camaraId}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dejarDeUsar(@PathVariable UUID camaraId) {
        camaras.dejarDeUsar(UsuarioActual.pcDelAgente(), camaraId);
    }

    @Operation(
            summary = "Renew the cameras' tokens (agent, US-29)",
            description =
                    "A new per-camera token for each camera in use of the PC, valid as long as the PC's access token.")
    @PostMapping(path = API + "/pc/camaras/tokens", version = ApiVersioning.V1)
    List<CamaraDeLaPcResponse> tokens() {
        return camaras.renovarTokens(UsuarioActual.pcDelAgente()).stream()
                .map(CamaraDeLaPcResponse::de)
                .toList();
    }
}
