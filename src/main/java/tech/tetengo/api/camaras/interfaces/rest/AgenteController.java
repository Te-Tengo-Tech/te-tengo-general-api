package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.ConsultarEstadoDeCaptura;
import tech.tetengo.api.camaras.application.RegistrarCamaraDelAgente;
import tech.tetengo.api.camaras.application.RegistrarSenal;
import tech.tetengo.api.camaras.application.RegistroDeCamara;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** Household agent endpoints of {@code camaras} (AGENT_CONTRACT.md). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/agente")
@Tag(name = "Agente del hogar", description = "Endpoints of the household agent (AGENT_CONTRACT.md).")
class AgenteController {

    private final RegistrarCamaraDelAgente registrarCamara;
    private final ConsultarEstadoDeCaptura consultarEstadoDeCaptura;
    private final RegistrarSenal registrarSenal;

    AgenteController(
            RegistrarCamaraDelAgente registrarCamara,
            ConsultarEstadoDeCaptura consultarEstadoDeCaptura,
            RegistrarSenal registrarSenal) {
        this.registrarCamara = registrarCamara;
        this.consultarEstadoDeCaptura = consultarEstadoDeCaptura;
        this.registrarSenal = registrarSenal;
    }

    /** US-07: periodic heartbeat; updates the time of the last signal. */
    @Operation(
            summary = "Heartbeat (agent, US-07)",
            description =
                    "Updates the time of the last signal and brings a disconnected camera back (CA-07.1, CA-07.3).")
    @PostMapping(path = "/senal", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void senal() {
        registrarSenal.ejecutar(UsuarioActual.camaraId().orElseThrow());
    }

    @Operation(
            summary = "Register the camera (agent, US-06)",
            description =
                    "Trades the installation credential for a per-camera token (CA-06.1). Errors: 401 CREDENCIAL_INVALIDA.")
    @SecurityRequirements
    @PostMapping(path = "/camaras/registro", version = ApiVersioning.V1)
    RegistroDeCamaraResponse registrar(@Valid @RequestBody RegistrarCamaraRequest pedido) {
        RegistroDeCamara registro = registrarCamara.ejecutar(pedido.credencial(), pedido.nombreHabitacion());
        return new RegistroDeCamaraResponse(registro.camaraId(), registro.token(), registro.expiraEn());
    }

    @Operation(
            summary = "Capture state (agent, US-05, US-22)",
            description =
                    "Whether the agent may process video: a current consent (CA-05.2) and no active pause (CA-22.1).")
    @GetMapping(path = "/estado-captura", version = ApiVersioning.V1)
    EstadoDeCapturaResponse estadoDeCaptura() {
        var estado = consultarEstadoDeCaptura.ejecutar(UsuarioActual.camaraId().orElseThrow());
        return new EstadoDeCapturaResponse(
                estado.capturaPermitida(), estado.consentimientoVigente(), estado.pausadaHasta());
    }
}
