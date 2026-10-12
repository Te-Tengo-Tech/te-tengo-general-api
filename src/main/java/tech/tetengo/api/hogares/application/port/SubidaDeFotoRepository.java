package tech.tetengo.api.hogares.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.hogares.domain.model.SubidaDeFoto;

/** Pending photo uploads (a global table). */
public interface SubidaDeFotoRepository {

    SubidaDeFoto guardar(SubidaDeFoto subida);

    Optional<SubidaDeFoto> buscar(UUID id);

    void eliminar(SubidaDeFoto subida);

    /** Uploads asked for before {@code instante}, oldest first, at most {@code limite}. */
    List<SubidaDeFoto> pedidasAntesDe(Instant instante, int limite);
}
