package tech.tetengo.api.alertas.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;

public interface AvisoPendienteRepository {

    AvisoPendiente guardar(AvisoPendiente aviso);

    /** Due notices of the household in context. */
    List<AvisoPendiente> vencidos(Instant ahora);

    void eliminar(AvisoPendiente aviso);

    /** Households with due notices: a native query across households, only for the retry job. */
    List<UUID> hogaresConVencidos(Instant ahora);
}
