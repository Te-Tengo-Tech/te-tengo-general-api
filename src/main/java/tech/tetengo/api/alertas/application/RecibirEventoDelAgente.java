package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.EventoDeAgenteRepository;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EventoDeAgente;
import tech.tetengo.api.alertas.domain.model.TipoEvento;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDelHogar;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-11 to US-21: applies an event of the household agent, idempotently by {@code eventoId}.
 *
 * <ul>
 *   <li>{@code caida}: a new fall alert (CA-11.1), or the camera's active unstable-movement alert
 *       becomes a fall (CA-14.3, CA-17.3).
 *   <li>{@code movimiento_inestable}: a medium-severity alert (CA-14.1, CA-17.1).
 *   <li>{@code caida_confirmada}: the fall is confirmed and stays active (CA-13.1).
 *   <li>{@code recuperacion}: the person got up (CA-13.2) and the family is told (CA-21.1).
 *   <li>{@code deteccion_no_confiable}: the camera's detection is not reliable (CA-15.3).
 * </ul>
 *
 * Events received without a current consent or during a pause create no alert (CA-05.2, CA-22.1).
 */
@Service
public class RecibirEventoDelAgente {

    private final EventoDeAgenteRepository eventos;
    private final AlertaRepository alertas;
    private final CamarasDelHogar camaras;
    private final EnvioDeAvisos avisos;
    private final Clock reloj;

    public RecibirEventoDelAgente(
            EventoDeAgenteRepository eventos,
            AlertaRepository alertas,
            CamarasDelHogar camaras,
            EnvioDeAvisos avisos,
            Clock reloj) {
        this.eventos = eventos;
        this.alertas = alertas;
        this.camaras = camaras;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    @Transactional
    public ResultadoDeEvento ejecutar(
            UUID camaraId, UUID eventoId, TipoEvento tipo, Instant ocurridoEn, String parametros) {
        Optional<EventoDeAgente> previo = eventos.buscarPorEventoId(eventoId);
        if (previo.isPresent()) {
            return new ResultadoDeEvento(eventoId, previo.get().getAlertaId(), null);
        }
        CamaraDelHogar camara =
                camaras.buscar(camaraId).orElseThrow(() -> new ErrorDeNegocio(AlertaError.CAMARA_NO_ENCONTRADA));
        EventoDeAgente evento = new EventoDeAgente(eventoId, camaraId, tipo, ocurridoEn, parametros);
        Efecto efecto = camara.capturaPermitida() ? aplicar(tipo, camara, ocurridoEn) : Efecto.NINGUNO;
        if (efecto.alerta() != null) {
            alertas.guardar(efecto.alerta());
            evento.asociarAlerta(efecto.alerta().getId());
        }
        eventos.guardar(evento);
        if (efecto.aviso() != null) {
            avisar(efecto, camara, ocurridoEn);
        }
        return new ResultadoDeEvento(
                eventoId, efecto.alerta() == null ? null : efecto.alerta().getId(), efecto.aviso());
    }

    /**
     * US-16, US-17: the push goes out within this request, so it reaches the family in less than
     * 10 s (CA-11.3, CA-16.1). If the push service fails, it is retried and the alert is still shown
     * when the app opens (CA-16.4).
     */
    private void avisar(Efecto efecto, CamaraDelHogar camara, Instant ocurridoEn) {
        Alerta alerta = efecto.alerta();
        ResultadoDeEnvio resultado = avisos.alHogar(new Aviso(
                efecto.aviso(),
                alerta == null ? null : alerta.getId(),
                camara.id(),
                alerta == null ? camara.nombreHabitacion() : alerta.getHabitacion(),
                ocurridoEn));
        if (resultado == ResultadoDeEnvio.ENTREGADO && alerta != null) {
            alerta.marcarNotificada(reloj.instant());
        }
    }

    private Efecto aplicar(TipoEvento tipo, CamaraDelHogar camara, Instant ocurridoEn) {
        if (tipo == TipoEvento.DETECCION_NO_CONFIABLE) {
            return camaras.marcarDeteccionNoConfiable(camara.id())
                    ? new Efecto(null, TipoAviso.DETECCION_NO_CONFIABLE)
                    : Efecto.NINGUNO;
        }
        // Any classified event means the agent sees the person again.
        camaras.marcarDeteccionConfiable(camara.id());
        return switch (tipo) {
            case CAIDA -> caida(camara, ocurridoEn);
            case MOVIMIENTO_INESTABLE ->
                new Efecto(
                        Alerta.movimientoInestable(camara.id(), camara.nombreHabitacion(), ocurridoEn),
                        TipoAviso.ALERTA_MOVIMIENTO_INESTABLE);
            case CAIDA_CONFIRMADA -> caidaConfirmada(camara, ocurridoEn);
            case RECUPERACION -> recuperacion(camara, ocurridoEn);
            case DETECCION_NO_CONFIABLE -> Efecto.NINGUNO;
        };
    }

    private Efecto caida(CamaraDelHogar camara, Instant ocurridoEn) {
        Optional<Alerta> inestable = alertas.inestableActivaDe(camara.id()).filter(Alerta::evolucionarACaida);
        if (inestable.isPresent()) {
            return new Efecto(inestable.get(), TipoAviso.ALERTA_ACTUALIZADA_A_CAIDA);
        }
        return new Efecto(Alerta.caida(camara.id(), camara.nombreHabitacion(), ocurridoEn), TipoAviso.ALERTA_CAIDA);
    }

    private Efecto caidaConfirmada(CamaraDelHogar camara, Instant ocurridoEn) {
        Optional<Alerta> porConfirmar = alertas.caidaPorConfirmarDe(camara.id()).filter(Alerta::confirmar);
        if (porConfirmar.isPresent()) {
            return new Efecto(porConfirmar.get(), TipoAviso.CAIDA_CONFIRMADA);
        }
        // The first fall event never arrived: this is a new, already confirmed fall.
        Alerta nueva = Alerta.caida(camara.id(), camara.nombreHabitacion(), ocurridoEn);
        nueva.confirmar();
        return new Efecto(nueva, TipoAviso.ALERTA_CAIDA);
    }

    /**
     * CA-13.2 and US-21: the recovery is recorded and the family gets the follow-up notice "se
     * levantó" (CA-21.1), except for a confirmed fall, which stays active as confirmed (CA-21.2).
     */
    private Efecto recuperacion(CamaraDelHogar camara, Instant ocurridoEn) {
        return alertas.caidaSinRecuperacionDe(camara.id())
                .filter(alerta -> alerta.registrarRecuperacion(ocurridoEn))
                .map(alerta -> new Efecto(alerta, alerta.isConfirmada() ? null : TipoAviso.SE_LEVANTO))
                .orElse(Efecto.NINGUNO);
    }

    private record Efecto(Alerta alerta, TipoAviso aviso) {
        static final Efecto NINGUNO = new Efecto(null, null);
    }
}
