package tech.tetengo.api.camaras.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.Pc;

/** PCs of the household in context, except the native lookups that say so. */
public interface PcRepository {

    Pc guardar(Pc pc);

    /** A linked PC of the household; unlinked PCs are not found. */
    Optional<Pc> buscar(UUID id);

    /** A PC of the household, linked or not (its name for the activity log). */
    Optional<Pc> buscarAunqueDesvinculada(UUID id);

    /** The same, locking the row until the transaction ends (concurrent unlink and renewal). */
    Optional<Pc> buscarParaActualizar(UUID id);

    /** Linked PCs of the household, oldest first. */
    List<Pc> vinculadas();

    /** Whether the PC is linked to that household: native, for {@code ComprobadorDeAgentes}. */
    boolean vinculada(UUID hogarId, UUID pcId);

    /** Linked PCs of every household with no tokens since {@code limite}: native, only for the job. */
    List<PcDeUnHogar> sinUsoDesde(Instant limite);

    record PcDeUnHogar(UUID pcId, UUID hogarId) {}
}
