package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.EstadoDeCamara;
import tech.tetengo.api.monitoreo.AlertasDeCamara;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.MonitoreoError;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-23: any member watches the camera live, from the home screen (CA-23.1) or from an alert of that
 * camera (CA-23.2). Not while it is disconnected (CA-23.3), paused, saying until when (CA-23.4), or
 * without consent (CA-05.2). The session is recorded for the access log (US-24) and the camera's
 * transmission starts if it was not running.
 */
@Service
public class AbrirVistaEnVivo {

    private final CamarasDelHogar camaras;
    private final AlertasDeCamara alertas;
    private final AccesoVistaEnVivoRepository accesos;
    private final Transmisiones transmisiones;
    private final PropiedadesDeVistaEnVivo propiedades;
    private final Clock reloj;

    public AbrirVistaEnVivo(
            CamarasDelHogar camaras,
            AlertasDeCamara alertas,
            AccesoVistaEnVivoRepository accesos,
            Transmisiones transmisiones,
            PropiedadesDeVistaEnVivo propiedades,
            Clock reloj) {
        this.camaras = camaras;
        this.alertas = alertas;
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** {@code modo} null keeps the stream's mode ({@code VIDEO} when it starts). */
    @Transactional
    public SesionDeVistaEnVivo ejecutar(UUID camaraId, UUID usuarioId, UUID alertaId, String modo) {
        ModoDeVista pedido = modo == null ? null : CambiarModoDeVistaEnVivo.modo(modo);
        Instant ahora = reloj.instant();
        EstadoDeCamara camara = estado(camaras, camaraId);
        if (alertaId != null && !alertas.esDeLaCamara(alertaId, camaraId)) {
            throw new ErrorDeNegocio(
                    MonitoreoError.VALIDACION, Map.of("campos", Map.of("alertaId", "La alerta no es de esta cámara.")));
        }
        verificarDisponible(camaras, camara, ahora);
        transmisiones.bloquear(camaraId);
        String token = Secretos.generar();
        AccesoVistaEnVivo acceso = accesos.guardar(new AccesoVistaEnVivo(
                camaraId, usuarioId, alertaId, ahora, propiedades.duracionMaxima(), Secretos.huella(token)));
        ModoDeVista modoDelStream = transmisiones.asegurar(camaraId, pedido, ahora);
        return new SesionDeVistaEnVivo(
                acceso.getId(),
                propiedades.urlTransmisionDe(camaraId, token),
                propiedades.urlWebrtcDe(camaraId, token).orElse(null),
                acceso.getExpiraEn(),
                modoDelStream);
    }

    /** The camera of the household, or {@code 404 CAMARA_NO_ENCONTRADA}. */
    static EstadoDeCamara estado(CamarasDelHogar camaras, UUID camaraId) {
        return camaras.estado(camaraId).orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.CAMARA_NO_ENCONTRADA));
    }

    /**
     * The camera may stream now: connected (CA-23.3), not paused (CA-23.4, with {@code pausadaHasta}) and with
     * the older adult's consent (CA-05.2); otherwise the matching {@code 409}.
     */
    static void verificarDisponible(CamarasDelHogar camaras, EstadoDeCamara camara, Instant ahora) {
        if ("DESCONECTADA".equals(camara.estadoConexion())) {
            throw new ErrorDeNegocio(MonitoreoError.CAMARA_DESCONECTADA);
        }
        if (camara.pausadaHasta() != null && ahora.isBefore(camara.pausadaHasta())) {
            throw new ErrorDeNegocio(MonitoreoError.CAMARA_EN_PAUSA, Map.of("pausadaHasta", camara.pausadaHasta()));
        }
        boolean capturaPermitida = camaras.buscar(camara.id())
                .map(CamaraDelHogar::capturaPermitida)
                .orElse(false);
        if (!capturaPermitida) {
            throw new ErrorDeNegocio(MonitoreoError.SIN_CONSENTIMIENTO);
        }
    }
}
