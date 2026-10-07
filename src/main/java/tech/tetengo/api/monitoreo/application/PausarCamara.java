package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.EstadoDeCamara;
import tech.tetengo.api.monitoreo.domain.MonitoreoError;
import tech.tetengo.api.monitoreo.domain.model.DuracionDePausa;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-22: any member pauses a camera for a set time, e.g. during a visit (CA-22.1), or resumes it
 * early. The app shows until when it is paused (CA-22.2).
 */
@Service
public class PausarCamara {

    private final CamarasDelHogar camaras;
    private final Clock reloj;

    public PausarCamara(CamarasDelHogar camaras, Clock reloj) {
        this.camaras = camaras;
        this.reloj = reloj;
    }

    @Transactional
    public EstadoDeCamara pausar(UUID camaraId, String duracion) {
        DuracionDePausa elegida =
                DuracionDePausa.desde(duracion).orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.DURACION_INVALIDA));
        return camaras.pausar(camaraId, elegida.hasta(reloj.instant()))
                .orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.CAMARA_NO_ENCONTRADA));
    }

    @Transactional
    public EstadoDeCamara reanudar(UUID camaraId) {
        return camaras.reanudar(camaraId).orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.CAMARA_NO_ENCONTRADA));
    }
}
