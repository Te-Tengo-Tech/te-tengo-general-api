package tech.tetengo.api.actividad.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.actividad.domain.ActividadError;
import tech.tetengo.api.actividad.domain.model.Actividad;
import tech.tetengo.api.actividad.domain.model.OrigenDeActividad;
import tech.tetengo.api.actividad.domain.model.TipoDeActividad;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-33 / CA-33.1, CA-33.3: the changes a PC makes on its own (advanced mode, local storage, local
 * deletions) reach the log, also those made without internet, sent when it returns. Each change has
 * the PC's own id, so a batch sent twice is stored once. Changes the API already sees (a camera's
 * room, unlinking) are logged by the API, not reported.
 *
 * <p>US-43: after installing an update the PC reports {@code PC_ACTUALIZADA} (old → new version), and
 * {@code ACTUALIZACION_AUTOMATICA_ACTIVADA} / {@code _DESACTIVADA} when «Actualizar automáticamente»
 * changes (CA-43.2, CA-43.7).
 *
 * <p>US-39 / CA-39.3: a replay watched on the PC ({@code REPETICION_VISTA}) carries the camera, the
 * moment replayed and how long it was watched; it is stored with the camera, the room as
 * {@code objeto}, the moment as an ISO-8601 interval in {@code valorAnterior} and the seconds
 * watched in {@code valorNuevo}.
 */
@Service
public class RegistrarActividadDeLaPc {

    /** The PC's clock may be a little ahead of the server's. */
    static final Duration TOLERANCIA = Duration.ofMinutes(5);

    private final RegistroDeActividad registro;
    private final CamarasDelHogar camaras;
    private final Clock reloj;

    public RegistrarActividadDeLaPc(RegistroDeActividad registro, CamarasDelHogar camaras, Clock reloj) {
        this.registro = registro;
        this.camaras = camaras;
        this.reloj = reloj;
    }

    public record Cambio(
            UUID id,
            String tipo,
            Instant ocurridoEn,
            String objeto,
            String valorAnterior,
            String valorNuevo,
            Repeticion repeticion) {}

    /** What a {@code REPETICION_VISTA} carries; null fields are validation errors. */
    public record Repeticion(UUID camaraId, Instant momentoDesde, Instant momentoHasta, Long duracionSegundos) {}

    public record Resultado(int registrados, int repetidos) {}

    @Transactional
    public Resultado ejecutar(UUID pcId, List<Cambio> cambios) {
        String nombre = camaras.nombreDePc(pcId).orElse(null);
        Instant limite = reloj.instant().plus(TOLERANCIA);
        int registrados = 0;
        for (int i = 0; i < cambios.size(); i++) {
            Cambio cambio = cambios.get(i);
            TipoDeActividad tipo = tipo(cambio.tipo(), i);
            if (cambio.ocurridoEn().isAfter(limite)) {
                throw invalido("cambios[%d].ocurridoEn".formatted(i), "La fecha está en el futuro.");
            }
            Actividad actividad = Actividad.de(
                            RegistroDeActividad.clave("PC", pcId, cambio.id()),
                            cambio.ocurridoEn(),
                            tipo,
                            OrigenDeActividad.PC)
                    .enPc(pcId, nombre);
            if (tipo == TipoDeActividad.REPETICION_VISTA) {
                repeticion(actividad, pcId, cambio, i, limite);
            } else {
                actividad.sobre(cambio.objeto(), cambio.valorAnterior(), cambio.valorNuevo());
            }
            boolean nuevo = registro.registrar(actividad);
            if (nuevo) {
                registrados++;
            }
        }
        return new Resultado(registrados, cambios.size() - registrados);
    }

    /** CA-39.3: the replay of a camera of this PC, of a past moment, watched for some seconds. */
    private void repeticion(Actividad actividad, UUID pcId, Cambio cambio, int i, Instant limite) {
        Repeticion r = cambio.repeticion() == null ? new Repeticion(null, null, null, null) : cambio.repeticion();
        if (r.camaraId() == null
                || !camaras.pcDeCamara(r.camaraId()).map(pcId::equals).orElse(false)) {
            throw invalido("cambios[%d].camaraId".formatted(i), "La cámara no es de esta PC.");
        }
        if (r.momentoDesde() == null
                || r.momentoHasta() == null
                || !r.momentoDesde().isBefore(r.momentoHasta())) {
            throw invalido("cambios[%d].momentoDesde".formatted(i), "El momento debe empezar antes de terminar.");
        }
        if (r.momentoHasta().isAfter(limite)) {
            throw invalido("cambios[%d].momentoHasta".formatted(i), "La fecha está en el futuro.");
        }
        if (r.duracionSegundos() == null || r.duracionSegundos() < 0) {
            throw invalido("cambios[%d].duracionSegundos".formatted(i), "Falta cuánto tiempo se vio.");
        }
        String habitacion = cambio.objeto() != null && !cambio.objeto().isBlank()
                ? cambio.objeto()
                : camaras.buscar(r.camaraId())
                        .map(CamarasDelHogar.CamaraDelHogar::nombreHabitacion)
                        .orElse(null);
        actividad
                .deCamara(r.camaraId())
                .sobre(habitacion, r.momentoDesde() + "/" + r.momentoHasta(), String.valueOf(r.duracionSegundos()));
    }

    private static TipoDeActividad tipo(String tipo, int indice) {
        try {
            TipoDeActividad leido = TipoDeActividad.valueOf(tipo);
            if (leido.reportadoPorLaPc()) {
                return leido;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            // Falls through to the validation error.
        }
        throw invalido(
                "cambios[%d].tipo".formatted(indice),
                "La PC solo informa MODO_AVANZADO_ACTIVADO, MODO_AVANZADO_DESACTIVADO, GUARDADO_LOCAL_CAMBIADO, DATOS_LOCALES_ELIMINADOS, REPETICION_VISTA, PC_ACTUALIZADA, ACTUALIZACION_AUTOMATICA_ACTIVADA o ACTUALIZACION_AUTOMATICA_DESACTIVADA.");
    }

    private static ErrorDeNegocio invalido(String campo, String mensaje) {
        return new ErrorDeNegocio(ActividadError.VALIDACION, Map.of("campos", Map.of(campo, mensaje)));
    }
}
