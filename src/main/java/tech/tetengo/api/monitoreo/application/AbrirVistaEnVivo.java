package tech.tetengo.api.monitoreo.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.EstadoDeCamara;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.MonitoreoError;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-23: any member watches the camera live, from the home screen (CA-23.1) or from an alert
 * (CA-23.2). Not while it is disconnected (CA-23.3) or paused, saying until when (CA-23.4). The
 * session is recorded for the access log (US-24).
 */
@Service
public class AbrirVistaEnVivo {

    private final CamarasDelHogar camaras;
    private final AccesoVistaEnVivoRepository accesos;
    private final PropiedadesDeVistaEnVivo propiedades;
    private final Clock reloj;

    public AbrirVistaEnVivo(
            CamarasDelHogar camaras,
            AccesoVistaEnVivoRepository accesos,
            PropiedadesDeVistaEnVivo propiedades,
            Clock reloj) {
        this.camaras = camaras;
        this.accesos = accesos;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Transactional
    public SesionDeVistaEnVivo ejecutar(UUID camaraId, UUID usuarioId, UUID alertaId) {
        Instant ahora = reloj.instant();
        EstadoDeCamara camara =
                camaras.estado(camaraId).orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.CAMARA_NO_ENCONTRADA));
        if ("DESCONECTADA".equals(camara.estadoConexion())) {
            throw new ErrorDeNegocio(MonitoreoError.CAMARA_DESCONECTADA);
        }
        if (camara.pausadaHasta() != null && ahora.isBefore(camara.pausadaHasta())) {
            throw new ErrorDeNegocio(MonitoreoError.CAMARA_EN_PAUSA, Map.of("pausadaHasta", camara.pausadaHasta()));
        }
        boolean capturaPermitida =
                camaras.buscar(camaraId).map(CamaraDelHogar::capturaPermitida).orElse(false);
        if (!capturaPermitida) {
            throw new ErrorDeNegocio(MonitoreoError.SIN_CONSENTIMIENTO);
        }
        String token = Secretos.generar();
        AccesoVistaEnVivo acceso = accesos.guardar(new AccesoVistaEnVivo(
                camaraId, usuarioId, alertaId, ahora, propiedades.vigencia(), Secretos.huella(token)));
        URI url = URI.create(
                "%s/api/vista-en-vivo/%s/transmision?token=%s".formatted(propiedades.urlBase(), acceso.getId(), token));
        return new SesionDeVistaEnVivo(acceso.getId(), url, acceso.getExpiraEn());
    }
}
