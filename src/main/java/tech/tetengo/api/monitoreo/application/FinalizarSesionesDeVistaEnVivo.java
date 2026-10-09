package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.application.port.ServicioDeTransmision;
import tech.tetengo.api.monitoreo.application.port.ServicioDeTransmision.Lector;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * Scheduled job that ends the live view sessions nobody closed (API contract §4) and records how long
 * they lasted (US-24): the viewer has not read for {@code inactividad} (30 s), or the session reached its
 * maximum length (10 min). A viewer reads while MediaMTX authorizes it or while its HLS session keeps
 * receiving bytes (MediaMTX authorizes an HLS reader once, then serves it by session). The camera stops
 * streaming when its last session ends. Idempotent.
 */
@Service
public class FinalizarSesionesDeVistaEnVivo {

    private static final Logger log = LoggerFactory.getLogger(FinalizarSesionesDeVistaEnVivo.class);

    private final AccesoVistaEnVivoRepository accesos;
    private final Transmisiones transmisiones;
    private final ServicioDeTransmision servicio;
    private final EjecutorEnHogar enHogar;
    private final PropiedadesDeVistaEnVivo propiedades;
    private final Clock reloj;

    /** Bytes sent to each HLS session at the previous run. */
    private final Map<String, Long> bytesPorLector = new ConcurrentHashMap<>();

    public FinalizarSesionesDeVistaEnVivo(
            AccesoVistaEnVivoRepository accesos,
            Transmisiones transmisiones,
            ServicioDeTransmision servicio,
            EjecutorEnHogar enHogar,
            PropiedadesDeVistaEnVivo propiedades,
            Clock reloj) {
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.servicio = servicio;
        this.enHogar = enHogar;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.vista-en-vivo.revision:PT10S}")
    public void ejecutar() {
        List<UUID> hogares = accesos.hogaresConSesionesAbiertas();
        Set<String> leyendo = conActividad(hogares.isEmpty() ? List.of() : servicio.lectores());
        Instant ahora = reloj.instant();
        for (UUID hogar : hogares) {
            try {
                enHogar.ejecutar(hogar, () -> finalizarEnHogar(leyendo, ahora));
            } catch (RuntimeException e) {
                log.error("No se pudieron finalizar las sesiones de vista en vivo del hogar {}", hogar, e);
            }
        }
    }

    /** Token hashes of the HLS sessions that are new or sent bytes since the previous run. */
    private Set<String> conActividad(List<Lector> lectores) {
        Set<String> leyendo = new HashSet<>();
        Set<String> vigentes = new HashSet<>();
        for (Lector lector : lectores) {
            vigentes.add(lector.id());
            Long antes = bytesPorLector.put(lector.id(), lector.bytesEnviados());
            if (lector.huellaToken() != null && !Objects.equals(antes, lector.bytesEnviados())) {
                leyendo.add(lector.huellaToken());
            }
        }
        bytesPorLector.keySet().retainAll(vigentes);
        return leyendo;
    }

    private void finalizarEnHogar(Set<String> leyendo, Instant ahora) {
        Map<UUID, List<AccesoVistaEnVivo>> vencidas = new TreeMap<>();
        for (AccesoVistaEnVivo sesion : accesos.abiertas()) {
            if (leyendo.contains(sesion.getTokenHash())) {
                sesion.registrarActividad(ahora);
                accesos.guardar(sesion);
            }
            if (sesion.finPendiente(ahora, propiedades.inactividad()).isPresent()) {
                vencidas.computeIfAbsent(sesion.getCamaraId(), id -> new ArrayList<>())
                        .add(sesion);
            }
        }
        // Cameras in a fixed order, so two transactions never wait for each other's locks.
        vencidas.forEach((camaraId, sesiones) -> {
            transmisiones.bloquear(camaraId);
            sesiones.stream()
                    .sorted(Comparator.comparing(AccesoVistaEnVivo::getInicio))
                    .forEach(sesion -> sesion.finPendiente(ahora, propiedades.inactividad())
                            .ifPresent(fin -> transmisiones.finalizar(List.of(sesion), fin)));
            transmisiones.detenerSiNoQuedanSesiones(camaraId);
        });
    }
}
