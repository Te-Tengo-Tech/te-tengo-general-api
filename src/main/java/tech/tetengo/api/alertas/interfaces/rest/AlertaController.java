package tech.tetengo.api.alertas.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.application.CambiarEstadoDeAlerta;
import tech.tetengo.api.alertas.application.ConsultarAlertas;
import tech.tetengo.api.alertas.application.port.AlertaRepository.FiltroDeAlertas;
import tech.tetengo.api.alertas.domain.model.EstadoAlerta;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-25: alert history and detail; US-19: alert state. */
@RestController
@RequestMapping(ApiVersioning.BASE + "/alertas")
@Tag(name = "Alertas", description = "Alerts, their history and their state (US-16 to US-21, US-25).")
class AlertaController {

    private final ConsultarAlertas consultarAlertas;
    private final CambiarEstadoDeAlerta cambiarEstado;

    AlertaController(ConsultarAlertas consultarAlertas, CambiarEstadoDeAlerta cambiarEstado) {
        this.consultarAlertas = consultarAlertas;
        this.cambiarEstado = cambiarEstado;
    }

    /** Newest first. {@code pagina} starts at 0; {@code tamano} defaults to 20, at most 100. */
    @Operation(
            summary = "List alerts (US-25)",
            description =
                    "Filters tipo, estado, desde, hasta; paging pagina, tamano; newest first (CA-25.1 to CA-25.3). Active alerts are listed even if their push failed (CA-16.4).")
    @GetMapping(version = ApiVersioning.V1)
    PaginaDeAlertasResponse listar(
            @RequestParam(required = false)
                    @Pattern(regexp = "CAIDA|MOVIMIENTO_INESTABLE", message = "Tipo desconocido.")
                    String tipo,
            @RequestParam(required = false)
                    @Pattern(regexp = "ACTIVA|ATENDIDA|FALSA_ALARMA", message = "Estado desconocido.")
                    String estado,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página empieza en 0.") int pagina,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "Pide al menos un elemento.")
                    @Max(value = 100, message = "Pide como máximo 100 elementos.")
                    int tamano) {
        var filtro = new FiltroDeAlertas(
                tipo == null ? null : TipoAlerta.valueOf(tipo),
                estado == null ? null : EstadoAlerta.valueOf(estado),
                desde,
                hasta);
        var resultado = consultarAlertas.listar(filtro, pagina, tamano);
        return new PaginaDeAlertasResponse(
                resultado.elementos().stream().map(AlertaMapper::aRespuesta).toList(), resultado.total());
    }

    @Operation(summary = "Read an alert", description = "Errors: 404 ALERTA_NO_ENCONTRADA.")
    @GetMapping(path = "/{id}", version = ApiVersioning.V1)
    AlertaResponse detalle(@PathVariable UUID id) {
        return AlertaMapper.aRespuesta(consultarAlertas.detalle(id));
    }

    /** US-19 / CA-19.1: any member, invited ones too. */
    @Operation(
            summary = "Mark as attended (US-19)",
            description =
                    "Records who and when (CA-19.1) and pushes ALERTA_ATENDIDA to the other members (CA-19.3). Errors: 409 ALERTA_CERRADA.")
    @PostMapping(path = "/{id}/atencion", version = ApiVersioning.V1)
    AlertaResponse atender(@PathVariable UUID id) {
        return AlertaMapper.aRespuesta(cambiarEstado.atender(id, UsuarioActual.id()));
    }

    /** US-19 / CA-19.2. */
    @Operation(
            summary = "Mark as a false alarm (US-19)",
            description = "Excluded from the fall count (CA-19.2). Errors: 409 ALERTA_CERRADA.")
    @PostMapping(path = "/{id}/falsa-alarma", version = ApiVersioning.V1)
    AlertaResponse marcarFalsaAlarma(@PathVariable UUID id) {
        return AlertaMapper.aRespuesta(cambiarEstado.marcarFalsaAlarma(id, UsuarioActual.id()));
    }
}
