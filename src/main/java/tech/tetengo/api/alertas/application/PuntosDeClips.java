package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.alertas.application.port.EventoDeAgenteRepository;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EventoDeAgente;
import tech.tetengo.api.movimientos.ConsentimientoDeMovimientosRevocado;
import tech.tetengo.api.movimientos.DatosDeMovimiento;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * US-36: the body landmarks of an event's clip, a Parquet file next to the clip
 * ({@code clips/<alerta>.landmarks.parquet}). The agent uploads it with a pre-signed URL, only with the
 * consent to store movements in force (CA-36.2); it is deleted with its clip (CA-36.3) and when that
 * consent is revoked (they are movements, CA-35.4).
 */
@Service
public class PuntosDeClips {

    /** IANA media type of Apache Parquet. */
    public static final String CONTENT_TYPE = "application/vnd.apache.parquet";

    private final EventoDeAgenteRepository eventos;
    private final AlertaRepository alertas;
    private final AlmacenamientoDeClips almacenamiento;
    private final DatosDeMovimiento movimientos;
    private final PropiedadesDeClips propiedades;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public PuntosDeClips(
            EventoDeAgenteRepository eventos,
            AlertaRepository alertas,
            AlmacenamientoDeClips almacenamiento,
            DatosDeMovimiento movimientos,
            PropiedadesDeClips propiedades,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.eventos = eventos;
        this.alertas = alertas;
        this.almacenamiento = almacenamiento;
        this.movimientos = movimientos;
        this.propiedades = propiedades;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    public static String clave(UUID alertaId) {
        return "clips/%s.landmarks.parquet".formatted(alertaId);
    }

    /** The agent asks where to upload the landmarks of the event's clip. */
    @Transactional
    public SubidaDeClip prepararSubida(UUID camaraId, UUID eventoId) {
        EventoDeAgente evento = eventos.buscarPorEventoId(eventoId)
                .filter(e -> e.getCamaraId().equals(camaraId) && e.getAlertaId() != null)
                .orElseThrow(() -> new ErrorDeNegocio(AlertaError.EVENTO_NO_ENCONTRADO));
        if (!movimientos.consentimientoVigente()) {
            throw new ErrorDeNegocio(AlertaError.SIN_CONSENTIMIENTO_DE_MOVIMIENTOS);
        }
        Alerta alerta = alertas.buscar(evento.getAlertaId())
                .orElseThrow(() -> new ErrorDeNegocio(AlertaError.EVENTO_NO_ENCONTRADO));
        alerta.asignarPuntos(clave(alerta.getId()));
        alertas.guardar(alerta);
        Instant expira = reloj.instant().plus(propiedades.vigenciaUrlSubida());
        var subida = almacenamiento.urlDeSubida(alerta.getPuntosClave(), CONTENT_TYPE, expira);
        return new SubidaDeClip(subida.url(), subida.cabeceras(), expira);
    }

    /** CA-36.3: the clip goes, its landmarks too. Call inside the household's transaction. */
    public void eliminarDe(Alerta alerta) {
        String clave = alerta.quitarPuntos();
        if (clave != null) {
            almacenamiento.eliminar(clave);
        }
    }

    /** CA-35.4: the landmarks are movements: they go when the consent to store them is revoked. */
    @Async
    @TransactionalEventListener
    public void alRevocarseConsentimientoDeMovimientos(ConsentimientoDeMovimientosRevocado evento) {
        enHogar.ejecutar(
                evento.hogarId(),
                () -> alertas.conPuntos().forEach(alerta -> {
                    eliminarDe(alerta);
                    alertas.guardar(alerta);
                }));
    }
}
