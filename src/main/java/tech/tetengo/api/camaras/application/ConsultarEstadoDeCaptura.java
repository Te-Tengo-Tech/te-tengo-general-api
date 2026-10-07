package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.EstadoDeCapturaRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * The agent asks at startup, on reconnection and periodically: it does not process video without a
 * current consent (CA-05.2) or while its camera is paused (CA-22.1).
 */
@Service
public class ConsultarEstadoDeCaptura {

    private final EstadoDeCapturaRepository estados;
    private final CamaraRepository camaras;
    private final Clock reloj;

    public ConsultarEstadoDeCaptura(EstadoDeCapturaRepository estados, CamaraRepository camaras, Clock reloj) {
        this.estados = estados;
        this.camaras = camaras;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public EstadoDeCapturaDeCamara ejecutar(UUID camaraId) {
        Camara camara = camaras.buscar(camaraId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.NO_ENCONTRADA));
        boolean consentimiento =
                estados.actual().map(EstadoDeCaptura::isConsentimientoVigente).orElse(false);
        Instant ahora = reloj.instant();
        Instant pausadaHasta = camara.estaPausada(ahora) ? camara.getPausadaHasta() : null;
        MotivoSinCaptura motivo = !consentimiento
                ? MotivoSinCaptura.SIN_CONSENTIMIENTO
                : pausadaHasta != null ? MotivoSinCaptura.EN_PAUSA : null;
        return new EstadoDeCapturaDeCamara(motivo == null, motivo, pausadaHasta, camara.getNombreHabitacion());
    }
}
