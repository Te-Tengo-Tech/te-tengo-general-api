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
import tech.tetengo.api.shared.application.port.NotificadorPush.Resultado;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * Delivers push notices to the active devices of the members of the household in context (API
 * contract §7), right away. If the push service does not respond, the error is logged and the notice
 * is queued for retry (CA-16.4). Devices whose token the service rejects are deactivated, and new
 * provider addresses are stored with their device. The text of each attempt names the older adult
 * and the alert as the prototype does ({@link DetalleDeAvisos}). Callers outside a request bind the
 * household with {@code EjecutorEnHogar}.
 */
@Service
public class EnvioDeAvisos {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeAvisos.class);

    private final MiembrosDelHogar miembros;
    private final DispositivoRepository dispositivos;
    private final NotificadorPush notificador;
    private final AvisoPendienteRepository pendientes;
    private final PropiedadesDePush propiedades;
    private final DetalleDeAvisos detalles;
    private final Clock reloj;

    public EnvioDeAvisos(
            MiembrosDelHogar miembros,
            DispositivoRepository dispositivos,
            NotificadorPush notificador,
            AvisoPendienteRepository pendientes,
            PropiedadesDePush propiedades,
            DetalleDeAvisos detalles,
            Clock reloj) {
        this.miembros = miembros;
        this.dispositivos = dispositivos;
        this.notificador = notificador;
        this.pendientes = pendientes;
        this.propiedades = propiedades;
        this.detalles = detalles;
        this.reloj = reloj;
    }

    /** To every member of the household. */
    public ResultadoDeEnvio alHogar(Aviso aviso) {
        return enviar(aviso, null, null);
    }

    /** To every member except one (e.g. whoever attended the alert, CA-19.3). */
    public ResultadoDeEnvio alHogarExcepto(UUID excluido, Aviso aviso) {
        return enviar(aviso, null, excluido);
    }

    /** To specific members (e.g. the secondary contact on escalation, CA-20.1). */
    public ResultadoDeEnvio aUsuarios(Collection<UUID> usuarioIds, Aviso aviso) {
        return enviar(aviso, List.copyOf(usuarioIds), null);
    }

    /** One more attempt for a queued notice; it is never queued again from here. */
    ResultadoDeEnvio reintentar(AvisoPendiente pendiente) {
        Aviso aviso = new Aviso(
                TipoAviso.valueOf(pendiente.getTipo()),
                pendiente.getAlertaId(),
                pendiente.getCamaraId(),
                pendiente.getHabitacion(),
                pendiente.getOcurridaEn());
        return entregar(aviso, destinos(pendiente.getDestinatarios(), pendiente.getExcluido()));
    }

    private ResultadoDeEnvio enviar(Aviso aviso, List<UUID> destinatarios, UUID excluido) {
        ResultadoDeEnvio resultado = entregar(aviso, destinos(destinatarios, excluido));
        if (resultado.resuelto()) {
            return resultado;
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
        return ResultadoDeEnvio.PENDIENTE;
    }

    private ResultadoDeEnvio entregar(Aviso aviso, List<Destino> destinos) {
        if (destinos.isEmpty()) {
            log.info("Push {} sin dispositivos registrados", aviso.tipo());
            return ResultadoDeEnvio.SIN_DISPOSITIVOS;
        }
        Resultado resultado;
        try {
            resultado = notificador.enviar(destinos, detalles.completar(aviso));
        } catch (RuntimeException e) {
            log.error("Falló el envío del push {}; se reintentará", aviso.tipo(), e);
            return ResultadoDeEnvio.PENDIENTE;
        }
        actualizarDispositivos(resultado);
        if (resultado.aceptados() == 0) {
            log.info("Push {}: el servicio rechazó todos los dispositivos", aviso.tipo());
            return ResultadoDeEnvio.SIN_DISPOSITIVOS;
        }
        return ResultadoDeEnvio.ENTREGADO;
    }

    /** Never fails the delivery: the notice already left. */
    private void actualizarDispositivos(Resultado resultado) {
        try {
            resultado
                    .tokensInvalidos()
                    .forEach(token -> dispositivos.buscarPorToken(token).ifPresent(dispositivo -> {
                        dispositivo.desactivar();
                        dispositivos.guardar(dispositivo);
                        log.info(
                                "Dispositivo {} desactivado: el servicio de push rechazó su token",
                                dispositivo.getId());
                    }));
            resultado
                    .referencias()
                    .forEach((token, referencia) -> dispositivos
                            .buscarPorToken(token)
                            .ifPresent(dispositivo -> {
                                dispositivo.asignarReferenciaPush(referencia);
                                dispositivos.guardar(dispositivo);
                            }));
        } catch (RuntimeException e) {
            log.error("No se pudieron actualizar los dispositivos tras el push", e);
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
                        NotificadorPush.Plataforma.valueOf(d.getPlataforma().name()),
                        d.getReferenciaPush()))
                .toList();
    }
}
