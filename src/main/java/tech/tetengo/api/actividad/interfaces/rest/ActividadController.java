package tech.tetengo.api.actividad.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.actividad.application.ConsultarActividad;
import tech.tetengo.api.actividad.application.RegistrarActividadDeLaPc;
import tech.tetengo.api.actividad.domain.model.TipoDeActividad;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-33: the activity log in the app, and the PC's own changes. */
@RestController
@Tag(name = "Actividad", description = "Who changed what and when (US-33).")
class ActividadController {

    private final ConsultarActividad consultar;
    private final RegistrarActividadDeLaPc registrarDeLaPc;

    ActividadController(ConsultarActividad consultar, RegistrarActividadDeLaPc registrarDeLaPc) {
        this.consultar = consultar;
        this.registrarDeLaPc = registrarDeLaPc;
    }

    @Operation(
            summary = "Activity log (US-33)",
            description =
                    "Who changed what and when, from the app or a PC, newest first (CA-33.1, CA-33.2); filters pcId, tipo, desde, hasta; pagina from 0, tamano default 50, at most 100. Empty when there is none (CA-33.4). Nobody can edit or delete it. Any member.")
    @GetMapping(path = ApiVersioning.BASE + "/actividad", version = ApiVersioning.V1)
    PaginaDeActividadResponse listar(
            @RequestParam(required = false) UUID pcId,
            @RequestParam(required = false) TipoDeActividad tipo,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int tamano) {
        var resultado = consultar.ejecutar(pcId, tipo, desde, hasta, pagina, tamano);
        return new PaginaDeActividadResponse(
                resultado.elementos().stream().map(ActividadResponse::de).toList(), resultado.total());
    }

    @Operation(
            summary = "Report the PC's own changes (agent, US-33)",
            description =
                    "Changes only the PC knows about: MODO_AVANZADO_ACTIVADO, MODO_AVANZADO_DESACTIVADO, GUARDADO_LOCAL_CAMBIADO, DATOS_LOCALES_ELIMINADOS, and REPETICION_VISTA (a replay watched on the PC, with camaraId, momentoDesde, momentoHasta and duracionSegundos; CA-39.3), also those queued without internet (CA-33.3). Idempotent by each change's id. Up to 100 per request. Errors: 400 VALIDACION. PC token.")
    @PostMapping(path = ApiVersioning.BASE + "/agente/pc/actividad", version = ApiVersioning.V1)
    RegistrarActividadDeLaPc.Resultado reportar(@Valid @RequestBody CambiosRequest pedido) {
        return registrarDeLaPc.ejecutar(
                UsuarioActual.pcDelAgente(),
                pedido.cambios().stream()
                        .map(c -> new RegistrarActividadDeLaPc.Cambio(
                                c.id(),
                                c.tipo(),
                                c.ocurridoEn(),
                                c.objeto(),
                                c.valorAnterior(),
                                c.valorNuevo(),
                                new RegistrarActividadDeLaPc.Repeticion(
                                        c.camaraId(), c.momentoDesde(), c.momentoHasta(), c.duracionSegundos())))
                        .toList());
    }

    record PaginaDeActividadResponse(List<ActividadResponse> elementos, long total) {}

    record CambiosRequest(@NotEmpty @Size(max = 100) List<@Valid CambioRequest> cambios) {}

    record CambioRequest(
            @NotNull(message = "Falta el identificador del cambio.")
            UUID id,

            @NotNull(message = "Falta el tipo.") String tipo,
            @NotNull(message = "Falta la fecha.") Instant ocurridoEn,
            @Size(max = 120) String objeto,
            @Size(max = 200) String valorAnterior,
            @Size(max = 200) String valorNuevo,
            // REPETICION_VISTA only (CA-39.3).
            UUID camaraId,
            Instant momentoDesde,
            Instant momentoHasta,
            Long duracionSegundos) {}
}
