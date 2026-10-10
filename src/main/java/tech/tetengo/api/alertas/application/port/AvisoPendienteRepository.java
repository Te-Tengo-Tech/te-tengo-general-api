package tech.tetengo.api.alertas.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;

public interface AvisoPendienteRepository {

    AvisoPendiente guardar(AvisoPendiente aviso);

    /** A queued notice of the household in context. */
    Optional<AvisoPendiente> buscar(UUID id);

    /** Due notices of the household in context, oldest first. */
    List<AvisoPendiente> vencidos(Instant ahora);

    /** Every queued notice of the household in context. */
    List<AvisoPendiente> delHogar();

    void eliminar(AvisoPendiente aviso);

    /** Households with due notices: a native query across households, only for the retry job. */
    List<UUID> hogaresConVencidos(Instant ahora);
}
