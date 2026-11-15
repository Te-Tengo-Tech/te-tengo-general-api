package tech.tetengo.api.monitoreo.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.ConsultarAccesos;
import tech.tetengo.api.monitoreo.application.ConsultarAccesosDenegados;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-24: live view access log, and the attempts that were refused (ADR 0015). */
@RestController
@Tag(name = "Vista en vivo", description = "Live view and its access log (US-23, US-24).")
class AccesosController {

    private final ConsultarAccesos consultarAccesos;
    private final ConsultarAccesosDenegados consultarDenegados;

    AccesosController(ConsultarAccesos consultarAccesos, ConsultarAccesosDenegados consultarDenegados) {
        this.consultarAccesos = consultarAccesos;
        this.consultarDenegados = consultarDenegados;
    }

    @Operation(
            summary = "Live view access log (US-24)",
            description =
                    "Newest first (CA-24.2); empty when nobody watched (CA-24.3). The sessions of one «Ver todas» opening are one entry with mosaico true and the cameras seen in camaraIds (CA-41.6).")
    @GetMapping(path = ApiVersioning.BASE + "/accesos-vista-en-vivo", version = ApiVersioning.V1)
    List<AccesoResponse> listar() {
        return consultarAccesos.ejecutar().stream()
                .map(a -> new AccesoResponse(
                        new AccesoResponse.Usuario(a.usuarioId(), a.nombre()),
                        a.inicio(),
                        a.duracionSegundos(),
                        a.desdeAlerta(),
                        a.camaraId(),
                        a.vistaPrevia(),
                        a.camaraIds(),
                        a.mosaico()))
                .toList();
    }

    @Operation(
            summary = "Denied live view attempts on the household's cameras (ADR 0015)",
            description =
                    "Newest first; empty when none. canal API (the app) or MEDIAMTX (the stream's authorization hook, with accion and protocolo); motivo says why. usuario only when miembroDelHogar: a member of this household asked; anyone else is never identified.")
    @GetMapping(path = ApiVersioning.BASE + "/accesos-vista-en-vivo/denegados", version = ApiVersioning.V1)
    List<AccesoDenegadoResponse> denegados() {
        return consultarDenegados.ejecutar().stream()
                .map(d -> new AccesoDenegadoResponse(
                        d.momento(),
                        d.canal(),
                        d.motivo(),
                        d.accion(),
                        d.protocolo(),
                        d.camaraId(),
                        d.nombreHabitacion(),
                        d.miembroDelHogar(),
                        d.miembroDelHogar() ? new AccesoResponse.Usuario(d.usuarioId(), d.nombre()) : null))
                .toList();
    }
}
