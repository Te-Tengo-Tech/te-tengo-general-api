package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.alertas.application.port.EventoDeAgenteRepository;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EventoDeAgente;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * US-18: the agent asks where to upload the clip of an event (6 s before and 6 s after, CA-18.1) and
 * gets a pre-signed PUT URL. The clip belongs to the alert the event created or updated.
 */
@Service
public class PrepararSubidaDeClip {

    private final EventoDeAgenteRepository eventos;
    private final AlertaRepository alertas;
    private final AlmacenamientoDeClips almacenamiento;
    private final PropiedadesDeClips propiedades;
    private final Clock reloj;

    public PrepararSubidaDeClip(
            EventoDeAgenteRepository eventos,
            AlertaRepository alertas,
            AlmacenamientoDeClips almacenamiento,
            PropiedadesDeClips propiedades,
            Clock reloj) {
        this.eventos = eventos;
        this.alertas = alertas;
        this.almacenamiento = almacenamiento;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** The clips are MP4 (AGENT_CONTRACT.md) unless the agent says otherwise. */
    public static final String CONTENT_TYPE_POR_DEFECTO = "video/mp4";

    @Transactional
    public SubidaDeClip ejecutar(UUID camaraId, UUID eventoId, String contentType) {
        EventoDeAgente evento = eventos.buscarPorEventoId(eventoId)
                .filter(e -> e.getCamaraId().equals(camaraId) && e.getAlertaId() != null)
                .orElseThrow(() -> new ErrorDeNegocio(AlertaError.EVENTO_NO_ENCONTRADO));
        Alerta alerta = alertas.buscar(evento.getAlertaId())
                .orElseThrow(() -> new ErrorDeNegocio(AlertaError.EVENTO_NO_ENCONTRADO));
        String clave =
                "hogares/%s/alertas/%s/%s".formatted(HogarActual.obtener().orElseThrow(), alerta.getId(), eventoId);
        String anterior = alerta.asignarClip(clave);
        if (anterior != null && !anterior.equals(clave)) {
            almacenamiento.eliminar(anterior);
        }
        Instant expira = reloj.instant().plus(propiedades.vigenciaUrlSubida());
        var subida =
                almacenamiento.urlDeSubida(clave, contentType == null ? CONTENT_TYPE_POR_DEFECTO : contentType, expira);
        return new SubidaDeClip(subida.url(), subida.cabeceras(), expira);
    }
}
