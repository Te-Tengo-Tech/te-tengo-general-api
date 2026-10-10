package tech.tetengo.api.shared.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Implemented by {@code alertas}: the deletion of the household's recordings after a consent
 * revocation, so {@code hogares} can tell the app whether it is done (API contract §2,
 * {@code eliminacion}) even when the push {@code DATOS_ELIMINADOS} never reached the screen.
 */
public interface EliminacionesDeGrabaciones {

    /** The latest deletion of the household in context, if any. */
    Optional<Eliminacion> ultima();

    /** Recordings of the household in context that are not deleted yet. */
    long clipsGuardados();

    /**
     * A deletion scheduled at {@code programadaEn} (the revocation time). {@code terminadaEn} and
     * {@code clipsEliminados} (recordings it deleted) are null while it is pending.
     */
    record Eliminacion(Instant programadaEn, Instant terminadaEn, Long clipsEliminados) {}
}
