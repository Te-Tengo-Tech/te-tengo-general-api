package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.ConsultarEstadoDeCaptura;
import tech.tetengo.api.camaras.application.EstadoDeCapturaDeCamara;
import tech.tetengo.api.camaras.application.PropiedadesDelAgente;
import tech.tetengo.api.camaras.application.RegistrarCamaraDelAgente;
import tech.tetengo.api.camaras.application.RegistrarSenal;
import tech.tetengo.api.camaras.application.RegistroDeCamara;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** Household agent endpoints of {@code camaras} (AGENT_CONTRACT.md, "Request and response bodies"). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/agente")
@Tag(name = "Agente del hogar", description = "Endpoints of the household agent (AGENT_CONTRACT.md).")
class AgenteController {

    private static final Logger log = LoggerFactory.getLogger(AgenteController.class);

    private final RegistrarCamaraDelAgente registrarCamara;
    private final ConsultarEstadoDeCaptura consultarEstadoDeCaptura;
    private final RegistrarSenal registrarSenal;
    private final PropiedadesDelAgente propiedades;

    AgenteController(
            RegistrarCamaraDelAgente registrarCamara,
            ConsultarEstadoDeCaptura consultarEstadoDeCaptura,
            RegistrarSenal registrarSenal,
            PropiedadesDelAgente propiedades) {
        this.registrarCamara = registrarCamara;
        this.consultarEstadoDeCaptura = consultarEstadoDeCaptura;
        this.registrarSenal = registrarSenal;
        this.propiedades = propiedades;
    }

    /** US-07: periodic heartbeat; updates the time of the last signal and answers the capture state. */
    @Operation(
            summary = "Heartbeat (agent, US-07)",
            description =
                    "Updates the time of the last signal and brings a disconnected camera back (CA-07.1, CA-07.3); with webcamConectada=false the camera is disconnected right away (CA-07.2). Answers the capture state.")
    @PostMapping(path = "/senal", version = ApiVersioning.V1)
    EstadoDeCapturaResponse senal(@RequestBody(required = false) SenalRequest pedido) {
        boolean conectada = pedido == null || pedido.conectada();
        return aRespuesta(registrarSenal.ejecutar(UsuarioActual.camaraId().orElseThrow(), conectada));
    }

    @Operation(
            summary = "Register the camera (agent, US-06)",
            description =
                    "Trades the installation credential for a per-camera token (CA-06.1) and returns the room name stored in the backend (CA-06.2). Errors: 401 CREDENCIAL_INVALIDA, 400 VALIDACION.")
    @SecurityRequirements
    @PostMapping(path = "/camaras/registro", version = ApiVersioning.V1)
    RegistroDeCamaraResponse registrar(@Valid @RequestBody RegistrarCamaraRequest pedido) {
        RegistroDeCamara registro = registrarCamara.ejecutar(pedido.credencialInstalacion(), pedido.nombreHabitacion());
        log.info("Cámara {} registrada por el agente {}", registro.camaraId(), versionLegible(pedido.versionAgente()));
        return new RegistroDeCamaraResponse(
                registro.camaraId(),
                registro.hogarId(),
                registro.token(),
                registro.expiraEn(),
                registro.nombreHabitacion());
    }

    @Operation(
            summary = "Capture state (agent, US-05, US-22)",
            description =
                    "Whether the agent may process video: a current consent (CA-05.2) and no active pause (CA-22.1); motivo says why not.")
    @GetMapping(path = "/estado-captura", version = ApiVersioning.V1)
    EstadoDeCapturaResponse estadoDeCaptura() {
        return aRespuesta(
                consultarEstadoDeCaptura.ejecutar(UsuarioActual.camaraId().orElseThrow()));
    }

    @Operation(
            summary = "Remote configuration (agent)",
            description =
                    "Published agent version and classification thresholds; an empty umbrales keeps the agent's local values.")
    @GetMapping(path = "/configuracion", version = ApiVersioning.V1)
    ConfiguracionDelAgenteResponse configuracion() {
        return new ConfiguracionDelAgenteResponse(propiedades.versionPublicada(), propiedades.umbrales());
    }

    private static EstadoDeCapturaResponse aRespuesta(EstadoDeCapturaDeCamara estado) {
        return new EstadoDeCapturaResponse(
                estado.capturaPermitida(), estado.motivo(), estado.pausadaHasta(), estado.nombreHabitacion());
    }

    /** The version is free text sent by the agent: keep the log line short and on one line. */
    private static String versionLegible(String version) {
        if (version == null || version.isBlank()) {
            return "(sin versión)";
        }
        String limpia = version.strip().replaceAll("[\\r\\n\\t]", " ");
        return limpia.length() > 40 ? limpia.substring(0, 40) : limpia;
    }
}
