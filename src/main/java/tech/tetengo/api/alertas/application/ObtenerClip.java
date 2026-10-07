package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EstadoClip;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-18 / US-26: a member opens the clip of an alert through a short-lived pre-signed URL (CA-18.1,
 * CA-26.1). Without a stored clip the alert is shown without it (CA-18.2, {@code 404
 * CLIP_NO_DISPONIBLE}); a deleted one answers {@code 410 CLIP_ELIMINADO} (CA-26.3).
 */
@Service
public class ObtenerClip {

    private final AlertaRepository alertas;
    private final AlmacenamientoDeClips almacenamiento;
    private final EstadoDeClips estadoDeClips;
    private final PropiedadesDeClips propiedades;
    private final Clock reloj;

    public ObtenerClip(
            AlertaRepository alertas,
            AlmacenamientoDeClips almacenamiento,
            EstadoDeClips estadoDeClips,
            PropiedadesDeClips propiedades,
            Clock reloj) {
        this.alertas = alertas;
        this.almacenamiento = almacenamiento;
        this.estadoDeClips = estadoDeClips;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Transactional
    public UrlDeClip ejecutar(UUID alertaId, boolean descarga) {
        Alerta alerta =
                alertas.buscar(alertaId).orElseThrow(() -> new ErrorDeNegocio(AlertaError.ALERTA_NO_ENCONTRADA));
        EstadoClip estado = estadoDeClips.actualizar(alerta);
        if (estado == EstadoClip.ELIMINADO) {
            throw new ErrorDeNegocio(AlertaError.CLIP_ELIMINADO);
        }
        if (estado == EstadoClip.NO_DISPONIBLE) {
            throw new ErrorDeNegocio(AlertaError.CLIP_NO_DISPONIBLE);
        }
        Instant expira = reloj.instant().plus(propiedades.vigenciaUrlLectura());
        return new UrlDeClip(
                almacenamiento.urlDeLectura(alerta.getClipClave(), expira, descarga, nombreDeArchivo(alerta)), expira);
    }

    private static String nombreDeArchivo(Alerta alerta) {
        return "te-tengo-%s-%s"
                .formatted(
                        alerta.getTipo().name().toLowerCase(java.util.Locale.ROOT),
                        alerta.getOcurridaEn().toString().replace(':', '-'));
    }
}
