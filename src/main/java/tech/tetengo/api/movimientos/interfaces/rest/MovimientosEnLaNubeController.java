package tech.tetengo.api.movimientos.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.movimientos.application.MovimientosEnLaNube;
import tech.tetengo.api.movimientos.application.MovimientosEnLaNubeConsultados;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-37: how long movements are kept in the cloud. */
@RestController
@RequestMapping(ApiVersioning.BASE + "/hogar/movimientos-en-la-nube")
@Tag(name = "Datos de movimiento", description = "Movement data: consent, cloud retention and replay (US-35 to US-39).")
class MovimientosEnLaNubeController {

    /** CA-37.1: the change request's estimate, 40 to 80 MB per camera and day. */
    private static final Estimado ESTIMADO = new Estimado(40, 80);

    private final MovimientosEnLaNube nube;

    MovimientosEnLaNubeController(MovimientosEnLaNube nube) {
        this.nube = nube;
    }

    @Operation(
            summary = "Read the cloud retention of movements (US-37)",
            description =
                    "retencionDias (null: not stored in the cloud, the default), the options, whether the consent is in force, since when there is data and how much (CA-37.1). Any member.")
    @GetMapping(version = ApiVersioning.V1)
    MovimientosEnLaNubeResponse ver() {
        return respuesta(nube.consultar());
    }

    @Operation(
            summary = "Choose the cloud retention of movements (owner, US-37)",
            description =
                    "retencionDias 7, 30, 90 or 365, or null to stop storing in the cloud. A shorter period deletes the older files at the next daily run (CA-37.4); null deletes them all. Errors: 422 RETENCION_INVALIDA, 409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS, 403 SOLO_TITULAR.")
    @PutMapping(version = ApiVersioning.V1)
    MovimientosEnLaNubeResponse cambiar(@RequestBody RetencionRequest pedido) {
        UsuarioActual.exigirTitular();
        return respuesta(nube.cambiar(pedido.retencionDias(), UsuarioActual.id()));
    }

    private static MovimientosEnLaNubeResponse respuesta(MovimientosEnLaNubeConsultados c) {
        return new MovimientosEnLaNubeResponse(
                c.retencionDias(),
                List.of(7, 30, 90, 365),
                365,
                ESTIMADO,
                c.consentimientoVigente(),
                c.datosDesde(),
                c.archivos(),
                c.tamanoBytes());
    }

    record RetencionRequest(Integer retencionDias) {}

    record Estimado(int minimo, int maximo) {}

    record MovimientosEnLaNubeResponse(
            Integer retencionDias,
            List<Integer> opciones,
            int maximoRecomendadoDias,
            Estimado mbPorCamaraPorDia,
            boolean consentimientoVigente,
            Instant datosDesde,
            long archivos,
            long tamanoBytes) {}
}
