package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDetectada;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.MonitoreoError;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-29 (product decision of 2026-10-10): a short preview of a webcam the PC detected, to recognise it
 * before assigning it a room from the app, also when it is not in use yet. It is a live view session
 * with the same rules (US-23: connected, not paused, the consent with its live view clause, CA-05.2),
 * the same playback (MediaMTX path {@code camaras/<camaraId>}; the webcam has its camera id since the PC
 * reported it), never recorded, and logged in the live view access log (US-24) as a preview. It lasts 2
 * minutes at most [implementation choice]. A webcam not in use publishes through its PC's channel.
 */
@Service
public class AbrirVistaPrevia {

    private final CamarasDelHogar camaras;
    private final AccesoVistaEnVivoRepository accesos;
    private final Transmisiones transmisiones;
    private final PropiedadesDeVistaEnVivo propiedades;
    private final Clock reloj;

    public AbrirVistaPrevia(
            CamarasDelHogar camaras,
            AccesoVistaEnVivoRepository accesos,
            Transmisiones transmisiones,
            PropiedadesDeVistaEnVivo propiedades,
            Clock reloj) {
        this.camaras = camaras;
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Transactional
    public SesionDeVistaEnVivo ejecutar(UUID camaraId, UUID usuarioId) {
        CamaraDetectada camara = camaras.buscarDetectada(camaraId)
                .orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.CAMARA_NO_ENCONTRADA));
        if (!camara.conectada()) {
            throw new ErrorDeNegocio(MonitoreoError.CAMARA_DESCONECTADA);
        }
        if (camara.pausadaHasta() != null) {
            throw new ErrorDeNegocio(MonitoreoError.CAMARA_EN_PAUSA, Map.of("pausadaHasta", camara.pausadaHasta()));
        }
        if (!camara.capturaPermitida()) {
            throw new ErrorDeNegocio(MonitoreoError.SIN_CONSENTIMIENTO);
        }
        Instant ahora = reloj.instant();
        transmisiones.bloquear(camaraId);
        String token = Secretos.generar();
        AccesoVistaEnVivo acceso = accesos.guardar(AccesoVistaEnVivo.vistaPrevia(
                camaraId, usuarioId, ahora, propiedades.duracionMaximaVistaPrevia(), Secretos.huella(token)));
        ModoDeVista modo = transmisiones.asegurar(camaraId, null, ahora);
        return new SesionDeVistaEnVivo(
                acceso.getId(),
                propiedades.urlTransmisionDe(camaraId, token),
                propiedades.urlWebrtcDe(camaraId, token).orElse(null),
                acceso.getExpiraEn(),
                modo);
    }
}
