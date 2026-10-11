package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.alertas.application.port.EliminacionDeGrabacionesRepository;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EliminacionDeGrabaciones;
import tech.tetengo.api.movimientos.DatosDeMovimiento;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * US-09: scheduled job that deletes every clip of the households whose consent was revoked
 * (CA-09.1), with their body landmarks (CA-36.3) and the movements stored in the cloud (US-37), and
 * then tells the family the data was deleted (CA-09.3). Idempotent: a completed deletion is not
 * repeated and its notice is sent once.
 */
@Service
public class EliminarGrabaciones {

    private static final Logger log = LoggerFactory.getLogger(EliminarGrabaciones.class);

    private final EliminacionDeGrabacionesRepository eliminaciones;
    private final AlertaRepository alertas;
    private final AlmacenamientoDeClips almacenamiento;
    private final EnvioDeAvisos avisos;
    private final PuntosDeClips puntos;
    private final DatosDeMovimiento movimientos;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public EliminarGrabaciones(
            EliminacionDeGrabacionesRepository eliminaciones,
            AlertaRepository alertas,
            AlmacenamientoDeClips almacenamiento,
            EnvioDeAvisos avisos,
            PuntosDeClips puntos,
            DatosDeMovimiento movimientos,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.eliminaciones = eliminaciones;
        this.alertas = alertas;
        this.almacenamiento = almacenamiento;
        this.avisos = avisos;
        this.puntos = puntos;
        this.movimientos = movimientos;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.clips.revision-de-eliminaciones:PT1M}")
    public void ejecutar() {
        for (UUID hogar : eliminaciones.hogaresConPendientes()) {
            try {
                enHogar.ejecutar(hogar, this::eliminarDelHogarActual);
            } catch (RuntimeException e) {
                log.error("No se pudieron eliminar las grabaciones del hogar {}", hogar, e);
            }
        }
    }

    private void eliminarDelHogarActual() {
        List<EliminacionDeGrabaciones> pendientes = eliminaciones.pendientes();
        if (pendientes.isEmpty()) {
            return;
        }
        Instant ahora = reloj.instant();
        List<Alerta> conClip = alertas.conClip();
        for (Alerta alerta : conClip) {
            almacenamiento.eliminar(alerta.getClipClave());
            alerta.marcarClipEliminado(ahora);
        }
        for (Alerta alerta : alertas.conPuntos()) {
            puntos.eliminarDe(alerta);
            alertas.guardar(alerta);
        }
        long archivos = movimientos.eliminarTodos();
        pendientes.forEach(p -> p.completar(ahora, conClip.size(), archivos));
        avisos.alHogar(new Aviso(TipoAviso.DATOS_ELIMINADOS, null, null, null, ahora));
    }
}
