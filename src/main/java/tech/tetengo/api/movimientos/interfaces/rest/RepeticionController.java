package tech.tetengo.api.movimientos.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.movimientos.application.PropiedadesDeMovimientos;
import tech.tetengo.api.movimientos.application.Rangos;
import tech.tetengo.api.movimientos.application.RepeticionAbierta;
import tech.tetengo.api.movimientos.application.Repeticiones;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-39: replay a camera's movements as a skeleton, and the replays' access log. */
@RestController
@Tag(name = "Datos de movimiento", description = "Movement data: consent, cloud retention and replay (US-35 to US-39).")
class RepeticionController {

    private static final String API = ApiVersioning.BASE;

    private final Repeticiones repeticiones;
    private final PropiedadesDeMovimientos propiedades;
    private final Clock reloj;

    RepeticionController(Repeticiones repeticiones, PropiedadesDeMovimientos propiedades, Clock reloj) {
        this.repeticiones = repeticiones;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Operation(
            summary = "Open a replay of a camera (US-39)",
            description =
                    "{desde, hasta} (at most 1 h) → the movement files of that moment with short-lived URLs (each with its light replay file in ligero once uploaded, ADR 0011), since when there is data (CA-39.4) and whether the cloud is on. Logged in the access log (CA-39.3); close it with DELETE. Errors: 409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS (CA-39.4), 404 CAMARA_NO_ENCONTRADA, 400 VALIDACION. Any member.")
    @PostMapping(path = API + "/camaras/{camaraId}/repeticiones", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    RepeticionAbierta abrir(@PathVariable UUID camaraId, @RequestBody MomentoRequest pedido) {
        Rangos.exigir(pedido.desde(), pedido.hasta(), propiedades.rangoMaximoConsulta(), reloj.instant());
        return repeticiones.abrir(UsuarioActual.id(), camaraId, pedido.desde(), pedido.hasta());
    }

    @Operation(
            summary = "More files of an open replay (US-39)",
            description =
                    "The member moved along the timeline: the files of desde-hasta (at most 1 h) with new URLs; the logged moment grows. Errors: 404 REPETICION_NO_ENCONTRADA (unknown, of another member, closed or past 1 h), 409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS, 400 VALIDACION.")
    @GetMapping(path = API + "/repeticiones/{id}/archivos", version = ApiVersioning.V1)
    RepeticionAbierta archivos(@PathVariable UUID id, @RequestParam Instant desde, @RequestParam Instant hasta) {
        Rangos.exigir(desde, hasta, propiedades.rangoMaximoConsulta(), reloj.instant());
        return repeticiones.archivos(UsuarioActual.id(), id, desde, hasta);
    }

    @Operation(
            summary = "Close a replay (US-39)",
            description =
                    "Records how long it lasted (CA-39.3). Closing someone else's, or a closed one, changes nothing.")
    @DeleteMapping(path = API + "/repeticiones/{id}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrar(@PathVariable UUID id) {
        repeticiones.cerrar(UsuarioActual.id(), id);
    }

    @Operation(
            summary = "Replays access log (US-39)",
            description =
                    "Who replayed which camera and moment, when and how long, newest first (CA-39.3). Any member.")
    @GetMapping(path = API + "/accesos-repeticiones", version = ApiVersioning.V1)
    List<AccesoARepeticionResponse> accesos() {
        return repeticiones.accesos().stream()
                .map(a -> new AccesoARepeticionResponse(
                        a.id(),
                        new AccesoARepeticionResponse.Usuario(a.usuarioId(), a.nombre()),
                        a.camaraId(),
                        a.habitacion(),
                        a.momentoDesde(),
                        a.momentoHasta(),
                        a.inicio(),
                        a.duracionSegundos()))
                .toList();
    }

    record MomentoRequest(Instant desde, Instant hasta) {}

    record AccesoARepeticionResponse(
            UUID id,
            Usuario usuario,
            UUID camaraId,
            String habitacion,
            Instant momentoDesde,
            Instant momentoHasta,
            Instant inicio,
            long duracionSegundos) {

        record Usuario(UUID id, String nombre) {}
    }
}
