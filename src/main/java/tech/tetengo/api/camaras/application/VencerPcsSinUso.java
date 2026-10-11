package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.AutorizacionDeDispositivoRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.model.MotivoDesvinculacion;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.multitenancy.ResolvedorDeHogar;

/**
 * Daily housekeeping of the PC link. CA-28.7: a PC that went 90 days or more without getting tokens is
 * unlinked (its refresh token expired, so it must be linked again; the app stops listing it).
 * Link requests expired for a day are deleted. Idempotent.
 */
@Service
public class VencerPcsSinUso {

    private static final Logger log = LoggerFactory.getLogger(VencerPcsSinUso.class);

    private final PcRepository pcs;
    private final AutorizacionDeDispositivoRepository autorizaciones;
    private final DesvinculacionDePcs desvinculacion;
    private final PropiedadesDeVinculacion propiedades;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public VencerPcsSinUso(
            PcRepository pcs,
            AutorizacionDeDispositivoRepository autorizaciones,
            DesvinculacionDePcs desvinculacion,
            PropiedadesDeVinculacion propiedades,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.pcs = pcs;
        this.autorizaciones = autorizaciones;
        this.desvinculacion = desvinculacion;
        this.propiedades = propiedades;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${tetengo.pcs.revision:0 30 3 * * *}", zone = "America/Lima")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(propiedades.vigenciaTokenRenovacion());
        for (var candidata : pcs.sinUsoDesde(limite)) {
            try {
                enHogar.ejecutar(
                        candidata.hogarId(),
                        () -> pcs.buscarParaActualizar(candidata.pcId())
                                .filter(pc -> pc.vencida(limite))
                                .ifPresent(pc ->
                                        desvinculacion.desvincular(pc, MotivoDesvinculacion.VENCIDA, null, ahora)));
            } catch (RuntimeException e) {
                log.error("No se pudo vencer la PC {}", candidata.pcId(), e);
            }
        }
        enHogar.ejecutar(
                ResolvedorDeHogar.SIN_HOGAR,
                () -> autorizaciones.eliminarVencidasAntesDe(ahora.minus(Duration.ofDays(1))));
    }
}
