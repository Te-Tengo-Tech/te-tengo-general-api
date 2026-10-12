package tech.tetengo.api.monitoreo.application.port;

import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.MosaicoDeVistaEnVivo;

/** «Ver todas» openings of the household in context (US-41). */
public interface MosaicoDeVistaEnVivoRepository {

    MosaicoDeVistaEnVivo guardar(MosaicoDeVistaEnVivo mosaico);

    Optional<MosaicoDeVistaEnVivo> buscar(UUID id);
}
