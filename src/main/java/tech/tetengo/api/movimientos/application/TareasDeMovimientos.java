package tech.tetengo.api.movimientos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.movimientos.MovimientosEliminados;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.application.port.ConfiguracionDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.ConsentimientoDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.EliminacionDeMovimientosRepository;
import tech.tetengo.api.movimientos.domain.model.ConfiguracionDeMovimientos;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * The movement data jobs, household by household, idempotent:
 *
 * <ul>
 *   <li>CA-37.4, daily: files older than the household's retention are deleted from storage and from the
 *       index; without a retention or without the consent, all of them. Uploads never confirmed are
 *       deleted a day later.
 *   <li>CA-35.4, every minute: after a revocation of the consent, every cloud movement is deleted and
 *       the family is told ({@code MovimientosEliminados}, push {@code MOVIMIENTOS_ELIMINADOS}).
 * </ul>
 */
@Service
public class TareasDeMovimientos {

    private static final Logger log = LoggerFactory.getLogger(TareasDeMovimientos.class);
    static final Duration ESPERA_DE_PENDIENTES = Duration.ofDays(1);

    private final ArchivoDeMovimientoRepository archivos;
    private final ConfiguracionDeMovimientosRepository configuraciones;
    private final ConsentimientoDeMovimientosRepository consentimientos;
    private final EliminacionDeMovimientosRepository eliminaciones;
    private final BorradoDeMovimientos borrado;
    private final ApplicationEventPublisher eventos;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public TareasDeMovimientos(
            ArchivoDeMovimientoRepository archivos,
            ConfiguracionDeMovimientosRepository configuraciones,
            ConsentimientoDeMovimientosRepository consentimientos,
            EliminacionDeMovimientosRepository eliminaciones,
            BorradoDeMovimientos borrado,
            ApplicationEventPublisher eventos,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.archivos = archivos;
        this.configuraciones = configuraciones;
        this.consentimientos = consentimientos;
        this.eliminaciones = eliminaciones;
        this.borrado = borrado;
        this.eventos = eventos;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    /** CA-37.4: the daily deletion by retention. */
    @Scheduled(cron = "${tetengo.movimientos.retencion-diaria:0 0 4 * * *}", zone = "America/Lima")
    public void aplicarRetencion() {
        for (UUID hogar : archivos.hogaresConArchivos()) {
            try {
                enHogar.ejecutar(hogar, () -> {
                    Instant ahora = reloj.instant();
                    boolean vigente = consentimientos
                            .ultimo()
                            .map(ConsentimientoDeMovimientos::vigente)
                            .orElse(false);
                    Instant limite = configuraciones
                            .actual()
                            .filter(c -> vigente)
                            .flatMap(ConfiguracionDeMovimientos::retencion)
                            .map(ahora::minus)
                            .orElse(ahora.plus(Duration.ofDays(1)));
                    long borrados = borrado.anterioresA(limite, ahora.minus(ESPERA_DE_PENDIENTES));
                    if (borrados > 0) {
                        log.info("Retención de movimientos: {} archivos eliminados en el hogar {}", borrados, hogar);
                    }
                });
            } catch (RuntimeException e) {
                log.error("No se pudo aplicar la retención de movimientos del hogar {}", hogar, e);
            }
        }
    }

    /** CA-35.4: the deletion after a revocation. */
    @Scheduled(fixedDelayString = "${tetengo.movimientos.revision-de-eliminaciones:PT1M}")
    public void eliminarRevocados() {
        for (UUID hogar : eliminaciones.hogaresConPendientes()) {
            try {
                enHogar.ejecutar(hogar, () -> {
                    var pendientes = eliminaciones.pendientes();
                    if (pendientes.isEmpty()) {
                        return;
                    }
                    Instant ahora = reloj.instant();
                    long borrados = borrado.todos();
                    pendientes.forEach(p -> {
                        p.terminar(ahora, borrados);
                        eliminaciones.guardar(p);
                    });
                    eventos.publishEvent(new MovimientosEliminados(hogar, borrados, ahora));
                });
            } catch (RuntimeException e) {
                log.error("No se pudieron eliminar los movimientos del hogar {}", hogar, e);
            }
        }
    }
}
