package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AvisoPendienteRepository;
import tech.tetengo.api.alertas.application.port.DispositivoRepository;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.Dispositivo;
import tech.tetengo.api.alertas.domain.model.Plataforma;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * API contract §7: a phone registers its push token to receive alerts (CA-16.1, CA-16.2), every time
 * the app starts or comes back, and reads whether the API still sends to it.
 */
@Service
public class RegistrarDispositivo {

    private final DispositivoRepository dispositivos;
    private final AvisoPendienteRepository pendientes;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public RegistrarDispositivo(
            DispositivoRepository dispositivos,
            AvisoPendienteRepository pendientes,
            ApplicationEventPublisher eventos,
            Clock reloj) {
        this.dispositivos = dispositivos;
        this.pendientes = pendientes;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Upsert by token: a new device, or the same one reactivated, reassigned and marked as seen now
     * even when nothing else changed. Notices of the household still waiting for a device (CA-16.4)
     * are attempted again right after the commit, so a phone that registers during a fall gets it.
     */
    @Transactional
    public Dispositivo ejecutar(UUID usuarioId, String tokenPush, Plataforma plataforma) {
        Instant ahora = reloj.instant();
        Dispositivo dispositivo = dispositivos
                .buscarPorToken(tokenPush)
                .map(existente -> {
                    existente.asignar(usuarioId, plataforma, ahora);
                    return existente;
                })
                .orElseGet(() -> new Dispositivo(tokenPush, usuarioId, plataforma, ahora));
        Dispositivo guardado = dispositivos.guardar(dispositivo);
        HogarActual.obtener()
                .ifPresent(hogar -> pendientes.delHogar().forEach(pendiente -> {
                    pendiente.adelantar(ahora);
                    eventos.publishEvent(new AvisoEncolado(hogar, pendiente.getId()));
                }));
        return guardado;
    }

    /** A device of the caller: whether the API still sends to it. Anyone else's is not found. */
    @Transactional(readOnly = true)
    public Dispositivo consultar(UUID usuarioId, UUID dispositivoId) {
        return dispositivos
                .buscar(dispositivoId)
                .filter(d -> d.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new ErrorDeNegocio(AlertaError.DISPOSITIVO_NO_ENCONTRADO));
    }

    /** Only the owner of the device can remove it; anything else is a no-op. */
    @Transactional
    public void eliminar(UUID usuarioId, String tokenPush) {
        dispositivos
                .buscarPorToken(tokenPush)
                .filter(d -> d.getUsuarioId().equals(usuarioId))
                .ifPresent(dispositivos::eliminar);
    }
}
