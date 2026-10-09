package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Preparar;
import tech.tetengo.api.monitoreo.application.port.TransmisionEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.MonitoreoError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * Live view v3: a member opened the camera screen, so a live view may start soon. The camera's agent gets
 * {@code {"preparar":true}} and warms up what stays on the PC; nothing is published and no session is
 * opened or recorded (US-24 records only opened sessions). The same rules as opening a session apply
 * (CA-23.3, CA-23.4, CA-05.2), so the app learns early that the camera cannot stream. Nothing is sent
 * while the camera already streams: the agent is publishing.
 */
@Service
public class PrepararVistaEnVivo {

    private final CamarasDelHogar camaras;
    private final TransmisionEnVivoRepository transmisiones;
    private final CanalDelAgente agentes;
    private final Clock reloj;

    public PrepararVistaEnVivo(
            CamarasDelHogar camaras, TransmisionEnVivoRepository transmisiones, CanalDelAgente agentes, Clock reloj) {
        this.camaras = camaras;
        this.transmisiones = transmisiones;
        this.agentes = agentes;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public void ejecutar(UUID camaraId) {
        if (camaraId == null) {
            throw new ErrorDeNegocio(
                    MonitoreoError.VALIDACION, Map.of("campos", Map.of("camaraId", "Indica la cámara.")));
        }
        AbrirVistaEnVivo.verificarDisponible(camaras, AbrirVistaEnVivo.estado(camaras, camaraId), reloj.instant());
        if (transmisiones.deCamara(camaraId).isEmpty()) {
            TrasConfirmar.ejecutar(() -> agentes.enviar(camaraId, new Preparar()));
        }
    }
}
