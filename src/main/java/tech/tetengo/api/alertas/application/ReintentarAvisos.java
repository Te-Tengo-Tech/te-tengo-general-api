package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.application.port.AvisoPendienteRepository;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * CA-16.4: scheduled job that retries the queued push notices that are due, household by household,
 * oldest first ({@link EnvioDeAvisos#intentar}). Idempotent: a delivered notice leaves the queue, and
 * a notice being attempted is not due.
 */
@Service
public class ReintentarAvisos {

    private static final Logger log = LoggerFactory.getLogger(ReintentarAvisos.class);

    private final AvisoPendienteRepository pendientes;
    private final EnvioDeAvisos envio;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public ReintentarAvisos(
            AvisoPendienteRepository pendientes, EnvioDeAvisos envio, EjecutorEnHogar enHogar, Clock reloj) {
        this.pendientes = pendientes;
        this.envio = envio;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.push.reintento-cada}")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        for (UUID hogar : pendientes.hogaresConVencidos(ahora)) {
            try {
                List<UUID> vencidos = enHogar.obtener(
                        hogar,
                        () -> pendientes.vencidos(ahora).stream()
                                .map(AvisoPendiente::getId)
                                .toList());
                for (UUID aviso : vencidos) {
                    reintentar(hogar, aviso);
                }
            } catch (RuntimeException e) {
                log.error("No se pudieron reintentar los avisos del hogar {}", hogar, e);
            }
        }
    }

    private void reintentar(UUID hogar, UUID aviso) {
        try {
            envio.intentar(hogar, aviso);
        } catch (RuntimeException e) {
            log.error("No se pudo reintentar el push {} del hogar {}", aviso, hogar, e);
        }
    }
}
