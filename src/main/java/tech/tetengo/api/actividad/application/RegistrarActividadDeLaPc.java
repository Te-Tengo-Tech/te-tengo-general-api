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
            UUID id, String tipo, Instant ocurridoEn, String objeto, String valorAnterior, String valorNuevo) {}

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
            boolean nuevo = registro.registrar(Actividad.de(
                            RegistroDeActividad.clave("PC", pcId, cambio.id()),
                            cambio.ocurridoEn(),
                            tipo,
                            OrigenDeActividad.PC)
                    .enPc(pcId, nombre)
                    .sobre(cambio.objeto(), cambio.valorAnterior(), cambio.valorNuevo()));
            if (nuevo) {
                registrados++;
            }
        }
        return new Resultado(registrados, cambios.size() - registrados);
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
                "La PC solo informa MODO_AVANZADO_ACTIVADO, MODO_AVANZADO_DESACTIVADO, GUARDADO_LOCAL_CAMBIADO o DATOS_LOCALES_ELIMINADOS.");
    }

    private static ErrorDeNegocio invalido(String campo, String mensaje) {
        return new ErrorDeNegocio(ActividadError.VALIDACION, Map.of("campos", Map.of(campo, mensaje)));
    }
}
