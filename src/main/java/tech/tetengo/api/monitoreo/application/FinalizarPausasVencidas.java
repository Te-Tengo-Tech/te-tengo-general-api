package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraEnHogar;
import tech.tetengo.api.monitoreo.PausaFinalizada;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * US-22 / CA-22.3: scheduled job that resumes capture when a pause is over and tells the family
 * ({@code PausaFinalizada} becomes the {@code PAUSA_FINALIZADA} push). Idempotent: a resumed camera
 * is left alone.
 */
@Service
public class FinalizarPausasVencidas {

    private static final Logger log = LoggerFactory.getLogger(FinalizarPausasVencidas.class);

    private final CamarasDelHogar camaras;
    private final EjecutorEnHogar enHogar;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public FinalizarPausasVencidas(
            CamarasDelHogar camaras, EjecutorEnHogar enHogar, ApplicationEventPublisher eventos, Clock reloj) {
        this.camaras = camaras;
        this.enHogar = enHogar;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.monitoreo.revision-de-pausas:PT30S}")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        for (CamaraEnHogar vencida : camaras.conPausaVencida(ahora)) {
            try {
                enHogar.ejecutar(
                        vencida.hogarId(),
                        () -> camaras.finalizarPausaSiVencio(vencida.camaraId(), ahora)
                                .ifPresent(camara -> eventos.publishEvent(new PausaFinalizada(
                                        vencida.hogarId(), camara.id(), camara.nombreHabitacion(), ahora))));
            } catch (RuntimeException e) {
                log.error("No se pudo reanudar la cámara {}", vencida.camaraId(), e);
            }
        }
    }
}
