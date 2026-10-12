package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.EstadoDeCamara;
import tech.tetengo.api.monitoreo.CamaraPausada;
import tech.tetengo.api.monitoreo.CamaraReanudada;
import tech.tetengo.api.monitoreo.domain.MonitoreoError;
import tech.tetengo.api.monitoreo.domain.model.DuracionDePausa;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * US-22: any member pauses a camera for a set time, e.g. during a visit (CA-22.1), or resumes it
 * early. The app shows until when it is paused (CA-22.2). A pause also stops live view at once.
 */
@Service
public class PausarCamara {

    private final CamarasDelHogar camaras;
    private final Transmisiones transmisiones;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public PausarCamara(
            CamarasDelHogar camaras, Transmisiones transmisiones, ApplicationEventPublisher eventos, Clock reloj) {
        this.camaras = camaras;
        this.transmisiones = transmisiones;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional
    public EstadoDeCamara pausar(UUID camaraId, String duracion) {
        return pausar(camaraId, duracion, null);
    }

    /** The same, recording who paused it for the activity log (US-33). */
    @Transactional
    public EstadoDeCamara pausar(UUID camaraId, String duracion, UUID usuarioId) {
        DuracionDePausa elegida =
                DuracionDePausa.desde(duracion).orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.DURACION_INVALIDA));
        Instant ahora = reloj.instant();
        EstadoDeCamara pausada = camaras.pausar(camaraId, elegida.hasta(ahora))
                .orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.CAMARA_NO_ENCONTRADA));
        transmisiones.detenerCamara(camaraId, ahora);
        eventos.publishEvent(new CamaraPausada(
                HogarActual.obtener().orElseThrow(),
                camaraId,
                pausada.nombreHabitacion(),
                pausada.pausadaHasta(),
                usuarioId,
                ahora));
        return pausada;
    }

    @Transactional
    public EstadoDeCamara reanudar(UUID camaraId) {
        return reanudar(camaraId, null);
    }

    /** The same, recording who resumed it for the activity log (US-33). */
    @Transactional
    public EstadoDeCamara reanudar(UUID camaraId, UUID usuarioId) {
        boolean estabaPausada = camaras.estado(camaraId)
                .map(c -> c.pausadaHasta() != null && reloj.instant().isBefore(c.pausadaHasta()))
                .orElse(false);
        EstadoDeCamara reanudada =
                camaras.reanudar(camaraId).orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.CAMARA_NO_ENCONTRADA));
        if (estabaPausada) {
            eventos.publishEvent(new CamaraReanudada(
                    HogarActual.obtener().orElseThrow(),
                    camaraId,
                    reanudada.nombreHabitacion(),
                    usuarioId,
                    reloj.instant()));
        }
        return reanudada;
    }
}
