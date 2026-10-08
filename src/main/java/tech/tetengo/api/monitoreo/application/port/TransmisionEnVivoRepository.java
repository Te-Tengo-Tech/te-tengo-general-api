package tech.tetengo.api.monitoreo.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.TransmisionEnVivo;

/** Current transmissions of the household in context, except {@link #hogarDeClave}. */
public interface TransmisionEnVivoRepository {

    Optional<TransmisionEnVivo> deCamara(UUID camaraId);

    List<TransmisionEnVivo> todas();

    TransmisionEnVivo guardar(TransmisionEnVivo transmision);

    void eliminar(TransmisionEnVivo transmision);

    /**
     * The household of the camera's transmission if {@code huella} is the hash of its publish token: a
     * native query, because MediaMTX's authorization request carries no JWT.
     */
    Optional<UUID> hogarDeClave(UUID camaraId, String huella);

    /**
     * Serializes the changes to the camera's sessions and transmission until the current transaction
     * ends (a PostgreSQL advisory lock), so a session that opens while the last one closes never finds
     * a transmission that is about to stop.
     */
    void bloquear(UUID camaraId);
}
