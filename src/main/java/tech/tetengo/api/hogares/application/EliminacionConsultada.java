package tech.tetengo.api.hogares.application;

import java.time.Instant;
import java.util.Optional;
import java.util.function.LongSupplier;
import tech.tetengo.api.shared.application.port.EliminacionesDeGrabaciones.Eliminacion;

/**
 * US-09: the deletion of the household's recordings after the latest consent revocation, as the app
 * shows it (CA-09.1, CA-09.3). The app polls it, because the push {@code DATOS_ELIMINADOS} does not
 * reach a screen that is in the background or a PWA whose window is hidden.
 *
 * @param clips recordings to delete ({@code PROGRAMADA}) or deleted ({@code TERMINADA})
 * @param terminadaEn null while {@code PROGRAMADA}
 */
public record EliminacionConsultada(Estado estado, long clips, Instant programadaEn, Instant terminadaEn) {

    public enum Estado {
        PROGRAMADA,
        TERMINADA
    }

    /**
     * The deletion of the latest revocation. {@code alertas} records it right after the revocation
     * commits, on another thread: until then, or when the latest deletion belongs to an earlier
     * revocation, the latest revocation's deletion is reported as scheduled at its revocation time.
     *
     * @param revocadoEn when the latest consent was revoked; empty if it was not
     * @param ultima the latest deletion recorded by {@code alertas}
     * @param clipsGuardados recordings not deleted yet, read only when the deletion is pending
     */
    public static Optional<EliminacionConsultada> de(
            Optional<Instant> revocadoEn, Optional<Eliminacion> ultima, LongSupplier clipsGuardados) {
        if (revocadoEn.isPresent()
                && ultima.map(e -> e.programadaEn().isBefore(revocadoEn.get())).orElse(true)) {
            return Optional.of(
                    new EliminacionConsultada(Estado.PROGRAMADA, clipsGuardados.getAsLong(), revocadoEn.get(), null));
        }
        return ultima.map(e -> e.terminadaEn() == null
                ? new EliminacionConsultada(Estado.PROGRAMADA, clipsGuardados.getAsLong(), e.programadaEn(), null)
                : new EliminacionConsultada(
                        Estado.TERMINADA,
                        e.clipsEliminados() == null ? 0 : e.clipsEliminados(),
                        e.programadaEn(),
                        e.terminadaEn()));
    }
}
