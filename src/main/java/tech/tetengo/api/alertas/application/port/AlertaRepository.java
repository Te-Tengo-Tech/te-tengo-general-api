package tech.tetengo.api.alertas.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EstadoAlerta;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;

/** Household data: queries are filtered by the household in context. */
public interface AlertaRepository {

    Alerta guardar(Alerta alerta);

    Optional<Alerta> buscar(UUID id);

    /** US-25: the household's alerts matching the filter, newest first (CA-25.1, CA-25.2). */
    Pagina<Alerta> buscar(FiltroDeAlertas filtro, int pagina, int tamano);

    record FiltroDeAlertas(TipoAlerta tipo, EstadoAlerta estado, Instant desde, Instant hasta) {}

    record Pagina<T>(List<T> elementos, long total) {}

    /** Alerts of the household in context whose clip has not been deleted. */
    List<Alerta> conClip();

    /** The most recent active unstable-movement alert of the camera (CA-17.3). */
    Optional<Alerta> inestableActivaDe(UUID camaraId);

    /** The most recent active, unconfirmed fall of the camera (CA-13.1). */
    Optional<Alerta> caidaPorConfirmarDe(UUID camaraId);

    /** The most recent fall of the camera the person has not got up from yet (CA-13.2). */
    Optional<Alerta> caidaSinRecuperacionDe(UUID camaraId);
}
