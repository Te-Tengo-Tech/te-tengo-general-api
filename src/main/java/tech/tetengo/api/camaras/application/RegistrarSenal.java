package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-07: the agent's periodic heartbeat keeps the camera online (CA-07.1) or brings it back
 * (CA-07.3). A heartbeat that reports the webcam as unavailable disconnects the camera right away
 * (CA-07.2). The answer is the capture state, so the agent learns about pauses and consent with
 * every heartbeat.
 */
@Service
public class RegistrarSenal {

    private final CamaraRepository camaras;
    private final PcRepository pcs;
    private final ConsultarEstadoDeCaptura estadoDeCaptura;
    private final Clock reloj;

    public RegistrarSenal(
            CamaraRepository camaras, PcRepository pcs, ConsultarEstadoDeCaptura estadoDeCaptura, Clock reloj) {
        this.camaras = camaras;
        this.pcs = pcs;
        this.estadoDeCaptura = estadoDeCaptura;
        this.reloj = reloj;
    }

    @Transactional
    public EstadoDeCapturaDeCamara ejecutar(UUID camaraId, boolean webcamConectada) {
        return ejecutar(camaraId, webcamConectada, null);
    }

    /**
     * Also keeps the agent version of the camera's PC up to date (CA-30.2 shows it), writing the PC
     * only when the version changed.
     */
    @Transactional
    public EstadoDeCapturaDeCamara ejecutar(UUID camaraId, boolean webcamConectada, String versionAgente) {
        Camara camara = camaras.buscar(camaraId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.NO_ENCONTRADA));
        if (webcamConectada) {
            camara.registrarSenal(reloj.instant());
        } else {
            camara.desconectar(reloj.instant());
        }
        camaras.guardar(camara);
        if (versionAgente != null && camara.getPcId() != null) {
            pcs.buscar(camara.getPcId())
                    .filter(pc -> pc.actualizarDatos(versionAgente, null))
                    .ifPresent(pcs::guardar);
        }
        return estadoDeCaptura.ejecutar(camaraId);
    }
}
