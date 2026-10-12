package tech.tetengo.api.alertas.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.application.ReportarCaidaSinAlerta;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-40 / CA-40.4: «¿Hubo una caída y no avisó?». */
@RestController
@Tag(name = "Alertas", description = "Alerts of the household.")
class ReporteDeCaidaController {

    private final ReportarCaidaSinAlerta reportar;

    ReporteDeCaidaController(ReportarCaidaSinAlerta reportar) {
        this.reportar = reportar;
    }

    @Operation(
            summary = "Report a fall the system did not warn about (US-40)",
            description =
                    "camaraId (or habitacion, or neither for «No sé»), the approximate time and an optional note (up to 300). Stored as a possible false negative for the evaluation; no alert, no notice (CA-40.4). Errors: 400 VALIDACION (campos.ocurridaAproximadamenteEn «Elige una hora que ya pasó.»), 404 CAMARA_NO_ENCONTRADA. Any member.")
    @PostMapping(path = ApiVersioning.BASE + "/reportes-de-caida", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    ReporteResponse reportar(@Valid @RequestBody ReporteRequest pedido) {
        var reporte = reportar.ejecutar(
                UsuarioActual.id(),
                pedido.camaraId(),
                pedido.habitacion(),
                pedido.ocurridaAproximadamenteEn(),
                pedido.nota());
        return new ReporteResponse(reporte.getId(), reporte.getCreadoEn(), reporte.isMovimientosDisponibles());
    }

    record ReporteRequest(
            UUID camaraId,
            @Size(max = 40) String habitacion,

            @NotNull(message = "Elige la fecha y la hora aproximadas.")
            Instant ocurridaAproximadamenteEn,

            @Size(max = 300, message = "Escribe hasta 300 caracteres.")
            String nota) {}

    record ReporteResponse(UUID id, Instant recibidoEn, boolean movimientosDisponibles) {}
}
