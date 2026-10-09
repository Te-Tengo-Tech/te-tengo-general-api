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

    /** Active, not yet escalated alerts of the household in context. */
    List<Alerta> activasSinEscalar();

    /**
     * Households with active, not yet escalated alerts that happened before {@code limite}: a native
     * query across households, only for the escalation job.
     */
    List<UUID> hogaresConActivasSinEscalarAntesDe(Instant limite);

    /** Alerts of the household in context whose clip has not been deleted. */
    List<Alerta> conClip();

    /** Alerts of the type that happened in {@code [desde, hasta)}, false alarms left out. */
    long contarSinFalsasAlarmas(TipoAlerta tipo, Instant desde, Instant hasta);

    /** False alarms that happened in {@code [desde, hasta)}. */
    long contarFalsasAlarmas(Instant desde, Instant hasta);

    /** Same, only for alerts that happened before {@code limite}. */
    List<Alerta> conClipAnteriorA(Instant limite);

    /** Households with clips of alerts older than {@code limite}: a native query, only for retention. */
    List<UUID> hogaresConClipsAnterioresA(Instant limite);

    /** The most recent active unstable-movement alert of the camera (CA-17.3). */
    Optional<Alerta> inestableActivaDe(UUID camaraId);

    /** The most recent active, unconfirmed fall of the camera (CA-13.1). */
    Optional<Alerta> caidaPorConfirmarDe(UUID camaraId);

    /** The most recent fall of the camera the person has not got up from yet (CA-13.2). */
    Optional<Alerta> caidaSinRecuperacionDe(UUID camaraId);
}
