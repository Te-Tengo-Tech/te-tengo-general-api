package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.camaras.PcDesvinculada;
import tech.tetengo.api.hogares.ConsentimientoRevocado;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Desvincular;
import tech.tetengo.api.monitoreo.application.port.TransmisionEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.TransmisionEnVivo;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * Live view reacts to the agent's control channel and to the household's consent: an agent that
 * connects while its camera has open sessions gets the transmission again, and a revoked consent
 * (CA-09.1) stops every camera of the household at once. An unlinked PC (US-31) stops its cameras'
 * live view and is told on its control channels.
 */
@Service
public class ControlDeVistaEnVivo {

    private final AccesoVistaEnVivoRepository accesos;
    private final TransmisionEnVivoRepository transmisionesGuardadas;
    private final Transmisiones transmisiones;
    private final CanalDelAgente agentes;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public ControlDeVistaEnVivo(
            AccesoVistaEnVivoRepository accesos,
            TransmisionEnVivoRepository transmisionesGuardadas,
            Transmisiones transmisiones,
            CanalDelAgente agentes,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.accesos = accesos;
        this.transmisionesGuardadas = transmisionesGuardadas;
        this.transmisiones = transmisiones;
        this.agentes = agentes;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    /**
     * US-31 / CA-31.1: the PC's cameras stop streaming, and every control channel of the PC (its own
     * and its cameras') gets {@code {"desvincular":true}} and is closed, so an online PC stops
     * capturing at once. An offline PC learns it at its next request ({@code 401 PC_DESVINCULADA}).
     */
    @Async
    @TransactionalEventListener
    public void alDesvincularsePc(PcDesvinculada evento) {
        enHogar.ejecutar(evento.hogarId(), () -> {
            evento.camaras().forEach(camara -> transmisiones.detenerCamara(camara, reloj.instant()));
            TrasConfirmar.ejecutar(() -> {
                evento.camaras().forEach(camara -> agentes.enviar(camara, new Desvincular()));
                agentes.enviar(evento.pcId(), new Desvincular());
            });
        });
    }

    /** The camera's agent opened its control channel. */
    public void alConectarseElAgente(UUID hogarId, UUID camaraId) {
        enHogar.ejecutar(hogarId, () -> transmisiones.alConectarseElAgente(camaraId, reloj.instant()));
    }

    /** US-09 / CA-09.1: without consent nothing is streamed. */
    @Async
    @TransactionalEventListener
    public void alRevocarseConsentimiento(ConsentimientoRevocado evento) {
        enHogar.ejecutar(evento.hogarId(), () -> {
            TreeSet<UUID> camaras = new TreeSet<>();
            accesos.abiertas().stream().map(AccesoVistaEnVivo::getCamaraId).forEach(camaras::add);
            transmisionesGuardadas.todas().stream()
                    .map(TransmisionEnVivo::getCamaraId)
                    .forEach(camaras::add);
            camaras.forEach(camara -> transmisiones.detenerCamara(camara, reloj.instant()));
        });
    }
}
