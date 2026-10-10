package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AvisoPendienteRepository;
import tech.tetengo.api.alertas.application.port.DispositivoRepository;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;
import tech.tetengo.api.hogares.MiembrosDelHogar;
import tech.tetengo.api.hogares.MiembrosDelHogar.Miembro;
import tech.tetengo.api.shared.application.port.NotificadorPush;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Destino;
import tech.tetengo.api.shared.application.port.NotificadorPush.Resultado;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * Delivers push notices to the active devices of the members of the household in context (API
 * contract §7) through an outbox:
 *
 * <ol>
 *   <li>{@link #alHogar}, {@link #alHogarExcepto} and {@link #aUsuarios} only queue the notice
 *       ({@link AvisoPendiente}) in the caller's transaction, so nothing is sent for a change that is
 *       rolled back and nothing is lost when the API stops.
 *   <li>Once that transaction commits, {@link DespachoDeAvisos} makes the first attempt on another
 *       thread ({@link #intentar}): never on the household agent's request thread, and never while
 *       a database transaction is open, since the push service may take seconds.
 *   <li>A notice that was not delivered is retried by {@link ReintentarAvisos} (CA-16.4) until its
 *       deadline: {@code tetengo.push.ventana-urgente} (30 min) for urgent alert notices, which are
 *       also retried while nobody in the family has an active device, so a phone that registers
 *       again still gets the fall; {@code reintento-cada × intentos-maximos} for the others.
 * </ol>
 *
 * A delivered notice that opens an alert marks it as notified ({@code notificadaEn},
 * {@code estadoAviso}). Devices whose token the service reports as gone are deactivated, and new
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
    private final AlertaRepository alertas;
    private final PropiedadesDePush propiedades;
    private final DetalleDeAvisos detalles;
    private final ApplicationEventPublisher eventos;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public EnvioDeAvisos(
            MiembrosDelHogar miembros,
            DispositivoRepository dispositivos,
            NotificadorPush notificador,
            AvisoPendienteRepository pendientes,
            AlertaRepository alertas,
            PropiedadesDePush propiedades,
            DetalleDeAvisos detalles,
            ApplicationEventPublisher eventos,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.miembros = miembros;
        this.dispositivos = dispositivos;
        this.notificador = notificador;
        this.pendientes = pendientes;
        this.alertas = alertas;
        this.propiedades = propiedades;
        this.detalles = detalles;
        this.eventos = eventos;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    /** To every member of the household. */
    public void alHogar(Aviso aviso) {
        encolar(aviso, null, null);
    }

    /** To every member except one (e.g. whoever attended the alert, CA-19.3). */
    public void alHogarExcepto(UUID excluido, Aviso aviso) {
        encolar(aviso, null, excluido);
    }

    /** To specific members (e.g. the secondary contact on escalation, CA-20.1). */
    public void aUsuarios(Collection<UUID> usuarioIds, Aviso aviso) {
        encolar(aviso, List.copyOf(usuarioIds), null);
    }

    private void encolar(Aviso aviso, List<UUID> destinatarios, UUID excluido) {
        UUID hogar = HogarActual.obtener()
                .orElseThrow(() -> new IllegalStateException("Push " + aviso.tipo() + " sin hogar en contexto"));
        Instant ahora = reloj.instant();
        AvisoPendiente pendiente = pendientes.guardar(new AvisoPendiente(
                aviso.tipo().name(),
                aviso.alertaId(),
                aviso.camaraId(),
                aviso.habitacion(),
                aviso.ocurridaEn(),
                destinatarios,
                excluido,
                ahora.plus(propiedades.reintentoCada()),
                ahora.plus(ventana(aviso.tipo()))));
        eventos.publishEvent(new AvisoEncolado(hogar, pendiente.getId()));
    }

    private Duration ventana(TipoAviso tipo) {
        return tipo.urgente()
                ? propiedades.ventanaUrgente()
                : propiedades.reintentoCada().multipliedBy(propiedades.intentosMaximos());
    }

    /**
     * One attempt of a queued notice of the household, if it is not being attempted already: it reads
     * the notice and its destinations in one transaction, sends it with none open, and records the
     * result in another. Returns the outcome, or null when there was nothing to attempt.
     */
    public ResultadoDeEnvio intentar(UUID hogarId, UUID avisoId) {
        Intento intento = enHogar.obtener(hogarId, () -> preparar(avisoId, reloj.instant()));
        if (intento == null) {
            return null;
        }
        Entrega entrega = entregar(intento);
        return enHogar.obtener(hogarId, () -> registrar(intento, entrega, reloj.instant()));
    }

    private Intento preparar(UUID avisoId, Instant ahora) {
        AvisoPendiente pendiente = pendientes.buscar(avisoId).orElse(null);
        if (pendiente == null || !pendiente.puedeIntentarse(ahora)) {
            return null;
        }
        TipoAviso tipo = TipoAviso.valueOf(pendiente.getTipo());
        Optional<Alerta> alerta = alertaDe(tipo, pendiente.getAlertaId());
        if (tipo.urgente()
                && pendiente.getIntentos() > 0
                && alerta.isPresent()
                && !alerta.get().activa()) {
            // Somebody already attended the alert: a late retry would only alarm the family.
            log.info(
                    "Push {} (alerta {}) sin entregar: la alerta ya se cerró; no se reintenta",
                    tipo,
                    pendiente.getAlertaId());
            if (tipo.abreAlerta()) {
                alerta.get().marcarAvisoNoEntregado();
            }
            pendientes.eliminar(pendiente);
            return null;
        }
        pendiente.iniciarIntento(ahora, propiedades.reintentoCada());
        Aviso aviso = detalles.completar(new Aviso(
                tipo,
                pendiente.getAlertaId(),
                pendiente.getCamaraId(),
                pendiente.getHabitacion(),
                pendiente.getOcurridaEn()));
        return new Intento(
                pendiente.getId(),
                pendiente.getIntentos(),
                aviso,
                destinos(pendiente.getDestinatarios(), pendiente.getExcluido()));
    }

    /** Outside any transaction. */
    private Entrega entregar(Intento intento) {
        if (intento.destinos().isEmpty()) {
            return new Entrega(null, false);
        }
        try {
            return new Entrega(notificador.enviar(intento.destinos(), intento.aviso()), false);
        } catch (RuntimeException e) {
            log.error(
                    "Falló el envío del push {} (alerta {}, intento {})",
                    intento.aviso().tipo(),
                    intento.aviso().alertaId(),
                    intento.numero(),
                    e);
            return new Entrega(null, true);
        }
    }

    private ResultadoDeEnvio registrar(Intento intento, Entrega entrega, Instant ahora) {
        if (entrega.resultado() != null) {
            actualizarDispositivos(entrega.resultado(), ahora);
        }
        Aviso aviso = intento.aviso();
        TipoAviso tipo = aviso.tipo();
        Optional<AvisoPendiente> pendiente = pendientes.buscar(intento.avisoId());
        Optional<Alerta> alerta = tipo.abreAlerta() ? alertaDe(tipo, aviso.alertaId()) : Optional.empty();
        if (entrega.aceptados() > 0) {
            log.info(
                    "Push {} (alerta {}) entregado a {} de {} dispositivo(s) en el intento {}",
                    tipo,
                    aviso.alertaId(),
                    entrega.aceptados(),
                    intento.destinos().size(),
                    intento.numero());
            pendiente.ifPresent(pendientes::eliminar);
            alerta.ifPresent(a -> a.marcarNotificada(ahora));
            return ResultadoDeEnvio.ENTREGADO;
        }
        ResultadoDeEnvio resultado = entrega.fallo() ? ResultadoDeEnvio.PENDIENTE : ResultadoDeEnvio.SIN_DISPOSITIVOS;
        if (pendiente.isEmpty()) {
            return resultado;
        }
        AvisoPendiente p = pendiente.get();
        if (resultado == ResultadoDeEnvio.SIN_DISPOSITIVOS && !tipo.urgente()) {
            log.warn("Push {} sin entregar: ningún destinatario tiene un dispositivo activo; no se reintenta", tipo);
            pendientes.eliminar(p);
            return resultado;
        }
        if (p.fallo(ahora, propiedades.reintentoCada())) {
            alerta.ifPresent(Alerta::marcarAvisoPendiente);
            String motivo = resultado == ResultadoDeEnvio.SIN_DISPOSITIVOS
                    ? "ningún destinatario tiene un dispositivo activo que lo acepte"
                    : "el servicio de push no lo aceptó";
            if (intento.numero() == 1) {
                log.error(
                        "Push {} (alerta {}) sin entregar: {}; se reintenta cada {} s hasta {}",
                        tipo,
                        aviso.alertaId(),
                        motivo,
                        propiedades.reintentoCada().toSeconds(),
                        p.getVenceEn());
            } else {
                log.warn(
                        "Push {} (alerta {}) sin entregar en el intento {}: {}",
                        tipo,
                        aviso.alertaId(),
                        intento.numero(),
                        motivo);
            }
            return resultado;
        }
        log.error(
                "Push {} (alerta {}) no entregado tras {} intento(s): se deja de reintentar",
                tipo,
                aviso.alertaId(),
                intento.numero());
        pendientes.eliminar(p);
        alerta.ifPresent(Alerta::marcarAvisoNoEntregado);
        return resultado;
    }

    /** Never fails the attempt: the notice already left. */
    private void actualizarDispositivos(Resultado resultado, Instant ahora) {
        try {
            resultado
                    .tokensInvalidos()
                    .forEach(token -> dispositivos.buscarPorToken(token).ifPresent(dispositivo -> {
                        dispositivo.desactivar(ahora);
                        dispositivos.guardar(dispositivo);
                        log.warn(
                                "Dispositivo {} ({}) desactivado: el servicio de push dice que su token ya no existe",
                                dispositivo.getId(),
                                dispositivo.getPlataforma());
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

    private Optional<Alerta> alertaDe(TipoAviso tipo, UUID alertaId) {
        return alertaId == null || !(tipo.urgente() || tipo.abreAlerta()) ? Optional.empty() : alertas.buscar(alertaId);
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

    /** What one attempt sends: read in a transaction, sent outside it. */
    private record Intento(UUID avisoId, int numero, Aviso aviso, List<Destino> destinos) {}

    /** What the push service answered; {@code fallo} when it did not respond. */
    private record Entrega(Resultado resultado, boolean fallo) {

        int aceptados() {
            return resultado == null ? 0 : resultado.aceptados();
        }
    }
}
