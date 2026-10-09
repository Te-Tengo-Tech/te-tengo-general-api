package tech.tetengo.api.historial.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.alertas.ConteoDeAlertas.Conteo;
import tech.tetengo.api.historial.application.ConsultarResumenSemanal;
import tech.tetengo.api.historial.application.ResumenSemanal;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-27: weekly summary. */
@RestController
@Tag(name = "Historial", description = "History and weekly summary (US-25 to US-27).")
class ResumenSemanalController {

    private final ConsultarResumenSemanal consultarResumen;

    ResumenSemanalController(ConsultarResumenSemanal consultarResumen) {
        this.consultarResumen = consultarResumen;
    }

    /** {@code semana} is an ISO week such as {@code 2026-W41}; the current week by default. */
    @Operation(
            summary = "Weekly summary (US-27)",
            description =
                    "semana is an ISO week (2026-W41), the current one by default; counts by type with false alarms apart (CA-27.1), zeros for an empty week (CA-27.2) and the trend against the previous week (CA-27.3).")
    @GetMapping(path = ApiVersioning.BASE + "/resumen-semanal", version = ApiVersioning.V1)
    ResumenSemanalResponse consultar(@RequestParam(required = false) String semana) {
        ResumenSemanal resumen = consultarResumen.ejecutar(semana);
        return new ResumenSemanalResponse(
                resumen.semana().toString(),
                conteos(resumen.conteos()),
                conteos(resumen.semanaAnterior()),
                new ResumenSemanalResponse.Tendencias(
                        resumen.tendencia().caidas().name(),
                        resumen.tendencia().movimientosInestables().name(),
                        resumen.tendencia().falsasAlarmas().name()));
    }

    private static ResumenSemanalResponse.Conteos conteos(Conteo conteo) {
        return new ResumenSemanalResponse.Conteos(
                conteo.caidas(), conteo.movimientosInestables(), conteo.falsasAlarmas());
    }
}
