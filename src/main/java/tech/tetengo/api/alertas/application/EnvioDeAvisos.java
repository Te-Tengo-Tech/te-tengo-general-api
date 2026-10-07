package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.application.port.AvisoPendienteRepository;
import tech.tetengo.api.alertas.application.port.DispositivoRepository;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;
import tech.tetengo.api.hogares.MiembrosDelHogar;
import tech.tetengo.api.hogares.MiembrosDelHogar.Miembro;
import tech.tetengo.api.shared.application.port.NotificadorPush;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Destino;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * Delivers push notices to the devices of the members of the household in context (API contract
 * §7), right away. If the push service does not respond, the error is logged and the notice is
 * queued for retry (CA-16.4). Callers outside a request bind the household with
 * {@code EjecutorEnHogar}.
 */
@Service
public class EnvioDeAvisos {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeAvisos.class);

    private final MiembrosDelHogar miembros;
    private final DispositivoRepository dispositivos;
    private final NotificadorPush notificador;
    private final AvisoPendienteRepository pendientes;
    private final PropiedadesDePush propiedades;
    private final Clock reloj;

    public EnvioDeAvisos(
            MiembrosDelHogar miembros,
            DispositivoRepository dispositivos,
            NotificadorPush notificador,
            AvisoPendienteRepository pendientes,
            PropiedadesDePush propiedades,
            Clock reloj) {
        this.miembros = miembros;
        this.dispositivos = dispositivos;
        this.notificador = notificador;
        this.pendientes = pendientes;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** To every member of the household. Returns whether the push service accepted it now. */
    public boolean alHogar(Aviso aviso) {
        return enviar(aviso, null, null);
    }

    /** To every member except one (e.g. whoever attended the alert, CA-19.3). */
    public boolean alHogarExcepto(UUID excluido, Aviso aviso) {
        return enviar(aviso, null, excluido);
    }

    /** To specific members (e.g. the secondary contact on escalation, CA-20.1). */
    public boolean aUsuarios(Collection<UUID> usuarioIds, Aviso aviso) {
        return enviar(aviso, List.copyOf(usuarioIds), null);
    }

    /** One more attempt for a queued notice; true if it was delivered. */
    boolean reintentar(AvisoPendiente pendiente) {
        Aviso aviso = new Aviso(
                TipoAviso.valueOf(pendiente.getTipo()),
                pendiente.getAlertaId(),
                pendiente.getCamaraId(),
                pendiente.getHabitacion(),
                pendiente.getOcurridaEn());
        return entregar(aviso, destinos(pendiente.getDestinatarios(), pendiente.getExcluido()));
    }

    private boolean enviar(Aviso aviso, List<UUID> destinatarios, UUID excluido) {
        if (entregar(aviso, destinos(destinatarios, excluido))) {
            return true;
        }
        pendientes.guardar(new AvisoPendiente(
                aviso.tipo().name(),
                aviso.alertaId(),
                aviso.camaraId(),
                aviso.habitacion(),
                aviso.ocurridaEn(),
                destinatarios,
                excluido,
                reloj.instant().plus(propiedades.reintentoCada())));
        return false;
    }

    private boolean entregar(Aviso aviso, List<Destino> destinos) {
        if (destinos.isEmpty()) {
            log.info("Push {} sin dispositivos registrados", aviso.tipo());
            return true;
        }
        try {
            notificador.enviar(destinos, aviso);
            return true;
        } catch (RuntimeException e) {
            log.error("Falló el envío del push {}; se reintentará", aviso.tipo(), e);
            return false;
        }
    }

    private List<Destino> destinos(List<UUID> destinatarios, UUID excluido) {
        Collection<UUID> usuarios = destinatarios != null
                ? destinatarios
                : HogarActual.obtener()
                        .map(hogar -> miembros.de(hogar).stream()
                                .map(Miembro::usuarioId)
                                .toList())
                        .orElse(List.of());
        Set<UUID> elegidos =
                usuarios.stream().filter(id -> !id.equals(excluido)).collect(Collectors.toSet());
        if (elegidos.isEmpty()) {
            return List.of();
        }
        return dispositivos.deUsuarios(elegidos).stream()
                .map(d -> new Destino(
                        d.getTokenPush(),
                        NotificadorPush.Plataforma.valueOf(d.getPlataforma().name())))
                .toList();
    }
}
