package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-19: any member (invited ones too) marks an active alert as attended (CA-19.1), which tells the
 * other members who attended it and when (CA-19.3), or as a false alarm (CA-19.2). A closed alert
 * answers {@code 409 ALERTA_CERRADA}.
 */
@Service
public class CambiarEstadoDeAlerta {

    private final AlertaRepository alertas;
    private final ConsultarAlertas consultarAlertas;
    private final EnvioDeAvisos avisos;
    private final Clock reloj;

    public CambiarEstadoDeAlerta(
            AlertaRepository alertas, ConsultarAlertas consultarAlertas, EnvioDeAvisos avisos, Clock reloj) {
        this.alertas = alertas;
        this.consultarAlertas = consultarAlertas;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    @Transactional
    public AlertaConsultada atender(UUID alertaId, UUID usuarioId) {
        Alerta alerta = buscar(alertaId);
        Instant ahora = reloj.instant();
        alerta.atender(usuarioId, ahora);
        alertas.guardar(alerta);
        avisos.alHogarExcepto(
                usuarioId,
                new Aviso(
                        TipoAviso.ALERTA_ATENDIDA,
                        alerta.getId(),
                        alerta.getCamaraId(),
                        alerta.getHabitacion(),
                        ahora));
        return consultarAlertas.consultada(alerta);
    }

    @Transactional
    public AlertaConsultada marcarFalsaAlarma(UUID alertaId, UUID usuarioId) {
        Alerta alerta = buscar(alertaId);
        alerta.marcarFalsaAlarma(usuarioId, reloj.instant());
        alertas.guardar(alerta);
        return consultarAlertas.consultada(alerta);
    }

    private Alerta buscar(UUID alertaId) {
        return alertas.buscar(alertaId).orElseThrow(() -> new ErrorDeNegocio(AlertaError.ALERTA_NO_ENCONTRADA));
    }
}
