package tech.tetengo.api.camaras.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.Ambiente;

/** Rooms of the household in context; deleted rooms are never found. */
public interface AmbienteRepository {

    Ambiente guardar(Ambiente ambiente);

    Optional<Ambiente> buscar(UUID id);

    /** By the lowercase name ({@link Ambiente#normalizar}). */
    Optional<Ambiente> porNombre(String nombreNormalizado);

    /** By name. */
    List<Ambiente> listar();
}
