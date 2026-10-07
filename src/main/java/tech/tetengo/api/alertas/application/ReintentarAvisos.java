package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AvisoPendienteRepository;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * CA-16.4: scheduled job that retries queued push notices, household by household. A delivered
 * alert notice marks the alert as notified. Idempotent: a delivered notice leaves the queue.
 */
@Service
public class ReintentarAvisos {

    private static final Logger log = LoggerFactory.getLogger(ReintentarAvisos.class);

    private final AvisoPendienteRepository pendientes;
    private final AlertaRepository alertas;
    private final EnvioDeAvisos envio;
    private final EjecutorEnHogar enHogar;
    private final PropiedadesDePush propiedades;
    private final Clock reloj;

    public ReintentarAvisos(
            AvisoPendienteRepository pendientes,
            AlertaRepository alertas,
            EnvioDeAvisos envio,
            EjecutorEnHogar enHogar,
            PropiedadesDePush propiedades,
            Clock reloj) {
        this.pendientes = pendientes;
        this.alertas = alertas;
        this.envio = envio;
        this.enHogar = enHogar;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.push.reintento-cada}")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        for (UUID hogar : pendientes.hogaresConVencidos(ahora)) {
            try {
                enHogar.ejecutar(hogar, () -> pendientes.vencidos(ahora).forEach(p -> reintentar(p, ahora)));
            } catch (RuntimeException e) {
                log.error("No se pudieron reintentar los avisos del hogar {}", hogar, e);
            }
        }
    }

    private void reintentar(AvisoPendiente pendiente, Instant ahora) {
        if (envio.reintentar(pendiente)) {
            if (pendiente.getAlertaId() != null) {
                alertas.buscar(pendiente.getAlertaId()).ifPresent(alerta -> alerta.marcarNotificada(ahora));
            }
            pendientes.eliminar(pendiente);
        } else if (!pendiente.fallo(ahora, propiedades.reintentoCada(), propiedades.intentosMaximos())) {
            log.error("Se agotaron los intentos del push {} (alerta {})", pendiente.getTipo(), pendiente.getAlertaId());
            pendientes.eliminar(pendiente);
        }
    }
}
