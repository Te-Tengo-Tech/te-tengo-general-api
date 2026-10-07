package tech.tetengo.api.alertas.interfaces.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.application.ConsultarAlertas;
import tech.tetengo.api.alertas.application.port.AlertaRepository.FiltroDeAlertas;
import tech.tetengo.api.alertas.domain.model.EstadoAlerta;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-25: alert history and detail. */
@RestController
@RequestMapping(ApiVersioning.BASE + "/alertas")
class AlertaController {

    private final ConsultarAlertas consultarAlertas;

    AlertaController(ConsultarAlertas consultarAlertas) {
        this.consultarAlertas = consultarAlertas;
    }

    /** Newest first. {@code pagina} starts at 0; {@code tamano} defaults to 20, at most 100. */
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

    @GetMapping(path = "/{id}", version = ApiVersioning.V1)
    AlertaResponse detalle(@PathVariable UUID id) {
        return AlertaMapper.aRespuesta(consultarAlertas.detalle(id));
    }
}
