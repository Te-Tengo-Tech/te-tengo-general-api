package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.ConsultarPcDelAgente;
import tech.tetengo.api.camaras.application.DesvincularPc;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** The linked PC's own endpoints, with its access token (AGENT_CONTRACT.md, "PC link"). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/agente/pc")
@Tag(name = "Agente del hogar", description = "Endpoints of the household agent (AGENT_CONTRACT.md).")
class PcDelAgenteController {

    private final ConsultarPcDelAgente consultar;
    private final DesvincularPc desvincular;

    PcDelAgenteController(ConsultarPcDelAgente consultar, DesvincularPc desvincular) {
        this.consultar = consultar;
        this.desvincular = desvincular;
    }

    @Operation(
            summary = "This PC's link (agent, US-28)",
            description =
                    "The household it is linked to (the older adult's name), who approved it and when, and whether the consent is in force (D-28.4). PC access token only: 403 TOKEN_DE_PC_REQUERIDO otherwise; 401 PC_DESVINCULADA once unlinked.")
    @GetMapping(version = ApiVersioning.V1)
    PcDelAgenteResponse ver() {
        return PcMapper.aRespuesta(consultar.ejecutar(UsuarioActual.pcDelAgente()));
    }

    @Operation(
            summary = "PC heartbeat (agent, US-30)",
            description =
                    "Every 30 s, like the cameras': keeps the PC online in the app even without cameras in use, and updates versionAgente and sistemaOperativo if sent. Answers the PC's link.")
    @PostMapping(path = "/senal", version = ApiVersioning.V1)
    PcDelAgenteResponse senal(@RequestBody(required = false) SenalDePcRequest pedido) {
        return PcMapper.aRespuesta(consultar.senal(
                UsuarioActual.pcDelAgente(),
                pedido == null ? null : pedido.versionAgente(),
                pedido == null ? null : pedido.sistemaOperativo()));
    }

    @Operation(
            summary = "Unlink this PC: «Desvincular esta PC» (agent, US-31)",
            description =
                    "Revokes the PC's tokens, retires its cameras and notifies the family with push PC_DESVINCULADA (CA-31.3). The PC then deletes its credentials and shows the link code. Query datosLocalesBorrados=true when the person also chose «Borrar también los datos guardados en esta PC» (CA-31.6): the PC deletes its data folder and the activity log records it.")
    @DeleteMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desvincular(@RequestParam(defaultValue = "false") boolean datosLocalesBorrados) {
        desvincular.desdeLaPc(UsuarioActual.pcDelAgente(), datosLocalesBorrados);
    }
}
