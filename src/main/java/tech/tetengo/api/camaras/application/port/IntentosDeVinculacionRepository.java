package tech.tetengo.api.camaras.application.port;

import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.IntentosDeVinculacion;

/** Wrong link codes per account (CA-28.6), a global table. */
public interface IntentosDeVinculacionRepository {

    IntentosDeVinculacion guardar(IntentosDeVinculacion intentos);

    /** Locking the row: two wrong codes typed at once both count. */
    Optional<IntentosDeVinculacion> deUsuarioParaActualizar(UUID usuarioId);
}
