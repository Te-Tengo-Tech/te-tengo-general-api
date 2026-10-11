package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.ResolverVinculacion;
import tech.tetengo.api.camaras.application.VinculacionConsultada;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/**
 * US-28: the owner approves a PC's code from the app. The code travels in the body, never in the URL,
 * so it stays out of access logs. Only the owner (CA-28.5).
 */
@RestController
@RequestMapping(ApiVersioning.BASE + "/vinculaciones")
@Tag(name = "PCs y cámaras", description = "The household's PCs and cameras (US-28 to US-31).")
class VinculacionController {

    private static final String ERRORES =
            " Errors: 400 VALIDACION (campos.codigo), 404 CODIGO_INCORRECTO {intentosRestantes} (CA-28.6 counts it), 410 CODIGO_VENCIDO (expired or used, CA-28.3), 423 VINCULACION_BLOQUEADA {bloqueadaHasta} (5 wrong codes in a row, 15 min, CA-28.6), 403 SOLO_TITULAR (CA-28.5).";

    private final ResolverVinculacion resolver;

    VinculacionController(ResolverVinculacion resolver) {
        this.resolver = resolver;
    }

    @Operation(
            summary = "Look up a PC's code before approving it (owner, US-28)",
            description = "The PC's name, system and agent version (CA-28.4), and when the code expires." + ERRORES)
    @PostMapping(path = "/consulta", version = ApiVersioning.V1)
    VinculacionConsultadaResponse consultar(@Valid @RequestBody CodigoDeVinculacionRequest pedido) {
        UsuarioActual.exigirTitular();
        VinculacionConsultada consultada = resolver.consultar(UsuarioActual.id(), pedido.codigo());
        return new VinculacionConsultadaResponse(
                consultada.codigo(),
                consultada.nombrePc(),
                consultada.sistemaOperativo(),
                consultada.versionAgente(),
                consultada.expiraEn());
    }

    @Operation(
            summary = "Approve the PC (owner, US-28)",
            description =
                    "Links the PC to the household (CA-28.1; other PCs keep their cameras, CA-30.1) and returns the new Pc; the PC gets its tokens at its next poll."
                            + ERRORES)
    @PostMapping(path = "/aprobacion", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    PcResponse aprobar(@Valid @RequestBody CodigoDeVinculacionRequest pedido) {
        UsuarioActual.exigirTitular();
        return PcMapper.aRespuesta(resolver.aprobar(UsuarioActual.id(), pedido.codigo()));
    }

    @Operation(
            summary = "Reject the PC: «No reconozco esta PC» (owner, US-28)",
            description = "The PC is not linked and gets access_denied (CA-28.4)." + ERRORES)
    @PostMapping(path = "/rechazo", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rechazar(@Valid @RequestBody CodigoDeVinculacionRequest pedido) {
        UsuarioActual.exigirTitular();
        resolver.rechazar(UsuarioActual.id(), pedido.codigo());
    }
}
