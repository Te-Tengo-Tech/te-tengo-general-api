package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.hogares.OrdenDeAviso;
import tech.tetengo.api.hogares.OrdenDeAviso.Configuracion;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * US-20: scheduled job. An alert still active once the household's wait is over (US-10) goes to the
 * secondary contact (CA-20.1); one attended in time does not (CA-20.2); without a secondary contact
 * the primary is told there is nobody to escalate to (CA-20.3). Idempotent: an alert is escalated
 * once.
 */
@Service
public class EscalarAlertas {

    private static final Logger log = LoggerFactory.getLogger(EscalarAlertas.class);

    private final AlertaRepository alertas;
    private final OrdenDeAviso ordenDeAviso;
    private final EnvioDeAvisos avisos;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public EscalarAlertas(
            AlertaRepository alertas,
            OrdenDeAviso ordenDeAviso,
            EnvioDeAvisos avisos,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.alertas = alertas;
        this.ordenDeAviso = ordenDeAviso;
        this.avisos = avisos;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.alertas.revision-de-escalamiento:PT15S}")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        // No household waits less than the shortest allowed time.
        Instant limite = ahora.minus(Duration.ofMinutes(OrdenDeAviso.ESPERA_MINIMA_MINUTOS));
        for (UUID hogar : alertas.hogaresConActivasSinEscalarAntesDe(limite)) {
            try {
                enHogar.ejecutar(hogar, () -> escalarEnHogar(hogar, ahora));
            } catch (RuntimeException e) {
                log.error("No se pudieron escalar las alertas del hogar {}", hogar, e);
            }
        }
    }

    private void escalarEnHogar(UUID hogar, Instant ahora) {
        Configuracion orden = ordenDeAviso.de(hogar);
        Duration espera = Duration.ofMinutes(orden.esperaMinutos());
        for (Alerta alerta : alertas.activasSinEscalar()) {
            if (alerta.escalarSiVencio(ahora, espera)) {
                boolean haySecundario = orden.secundarioId() != null;
                avisos.aUsuarios(
                        List.of(haySecundario ? orden.secundarioId() : orden.principalId()),
                        new Aviso(
                                haySecundario ? TipoAviso.ALERTA_ESCALADA : TipoAviso.SIN_CONTACTO_SECUNDARIO,
                                alerta.getId(),
                                alerta.getCamaraId(),
                                alerta.getHabitacion(),
                                alerta.getOcurridaEn()));
            }
        }
    }
}
