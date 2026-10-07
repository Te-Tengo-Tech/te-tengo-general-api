package tech.tetengo.api.alertas.application;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.application.port.DispositivoRepository;
import tech.tetengo.api.hogares.MiembrosDelHogar;
import tech.tetengo.api.hogares.MiembrosDelHogar.Miembro;
import tech.tetengo.api.shared.application.port.NotificadorPush;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Destino;

/**
 * Delivers push notices to the devices of a household's members (API contract §7). Returns whether
 * the push service accepted the notice; failures are logged.
 */
@Service
public class EnvioDeAvisos {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeAvisos.class);

    private final MiembrosDelHogar miembros;
    private final DispositivoRepository dispositivos;
    private final NotificadorPush notificador;

    public EnvioDeAvisos(MiembrosDelHogar miembros, DispositivoRepository dispositivos, NotificadorPush notificador) {
        this.miembros = miembros;
        this.dispositivos = dispositivos;
        this.notificador = notificador;
    }

    /** To every member of the household. */
    public boolean alHogar(UUID hogarId, Aviso aviso) {
        return aUsuarios(miembros.de(hogarId).stream().map(Miembro::usuarioId).toList(), aviso);
    }

    /** To every member of the household except one (e.g. whoever attended the alert). */
    public boolean alHogarExcepto(UUID hogarId, UUID excluido, Aviso aviso) {
        return aUsuarios(
                miembros.de(hogarId).stream()
                        .map(Miembro::usuarioId)
                        .filter(id -> !id.equals(excluido))
                        .toList(),
                aviso);
    }

    /** To specific members (e.g. the secondary contact on escalation). */
    public boolean aUsuarios(Collection<UUID> usuarioIds, Aviso aviso) {
        List<Destino> destinos = usuarioIds.isEmpty()
                ? List.of()
                : dispositivos.deUsuarios(Set.copyOf(usuarioIds)).stream()
                        .map(d -> new Destino(
                                d.getTokenPush(),
                                NotificadorPush.Plataforma.valueOf(
                                        d.getPlataforma().name())))
                        .toList();
        if (destinos.isEmpty()) {
            log.info("Push {} sin dispositivos registrados", aviso.tipo());
            return true;
        }
        try {
            notificador.enviar(destinos, aviso);
            return true;
        } catch (RuntimeException e) {
            log.error("Falló el envío del push {}", aviso.tipo(), e);
            return false;
        }
    }
}
