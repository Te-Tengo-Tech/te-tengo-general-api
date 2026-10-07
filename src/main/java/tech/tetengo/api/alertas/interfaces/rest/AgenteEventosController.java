package tech.tetengo.api.alertas.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.application.RecibirEventoDelAgente;
import tech.tetengo.api.alertas.application.ResultadoDeEvento;
import tech.tetengo.api.alertas.domain.model.TipoEvento;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;
import tools.jackson.databind.json.JsonMapper;

/** Household agent: detected events (AGENT_CONTRACT.md). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/agente/eventos")
@Tag(name = "Agente del hogar", description = "Endpoints of the household agent (AGENT_CONTRACT.md).")
class AgenteEventosController {

    private final RecibirEventoDelAgente recibirEvento;
    private final JsonMapper json;

    AgenteEventosController(RecibirEventoDelAgente recibirEvento, JsonMapper json) {
        this.recibirEvento = recibirEvento;
        this.json = json;
    }

    @Operation(
            summary = "Report a detected event (agent, US-11 to US-21)",
            description =
                    "Idempotent by eventoId; creates or updates alerts and pushes them within the request (CA-16.1).")
    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.ACCEPTED)
    EventoDelAgenteResponse recibir(@Valid @RequestBody EventoDelAgenteRequest pedido) {
        ResultadoDeEvento resultado = recibirEvento.ejecutar(
                UsuarioActual.camaraId().orElseThrow(),
                pedido.eventoId(),
                TipoEvento.desde(pedido.tipo()).orElseThrow(),
                pedido.ocurridoEn(),
                pedido.parametros() == null ? null : json.writeValueAsString(pedido.parametros()));
        return new EventoDelAgenteResponse(resultado.eventoId(), resultado.alertaId());
    }
}
