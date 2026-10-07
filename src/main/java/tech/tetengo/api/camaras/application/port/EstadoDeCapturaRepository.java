package tech.tetengo.api.camaras.application.port;

import java.util.Optional;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;

/** One row per household; queries are filtered by the household in context. */
public interface EstadoDeCapturaRepository {

    EstadoDeCaptura guardar(EstadoDeCaptura estado);

    Optional<EstadoDeCaptura> actual();
}
