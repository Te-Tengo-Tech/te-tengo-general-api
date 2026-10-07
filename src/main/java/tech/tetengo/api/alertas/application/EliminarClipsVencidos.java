package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.RetencionDeClips;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/** US-26: deletes clips past the retention period, household by household (CA-26.3). */
@Service
public class EliminarClipsVencidos implements RetencionDeClips {

    private static final Logger log = LoggerFactory.getLogger(EliminarClipsVencidos.class);

    private final AlertaRepository alertas;
    private final AlmacenamientoDeClips almacenamiento;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public EliminarClipsVencidos(
            AlertaRepository alertas, AlmacenamientoDeClips almacenamiento, EjecutorEnHogar enHogar, Clock reloj) {
        this.alertas = alertas;
        this.almacenamiento = almacenamiento;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    @Override
    public int eliminarAnterioresA(Instant limite) {
        int eliminados = 0;
        for (UUID hogar : alertas.hogaresConClipsAnterioresA(limite)) {
            try {
                eliminados += enHogar.obtener(hogar, () -> {
                    Instant ahora = reloj.instant();
                    int cantidad = 0;
                    for (Alerta alerta : alertas.conClipAnteriorA(limite)) {
                        almacenamiento.eliminar(alerta.getClipClave());
                        alerta.marcarClipEliminado(ahora);
                        cantidad++;
                    }
                    return cantidad;
                });
            } catch (RuntimeException e) {
                log.error("No se pudo aplicar la retención en el hogar {}", hogar, e);
            }
        }
        return eliminados;
    }
}
