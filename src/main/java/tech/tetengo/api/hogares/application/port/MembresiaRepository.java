package tech.tetengo.api.hogares.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.hogares.domain.model.Membresia;

public interface MembresiaRepository {

    /** Saves the membership; throws {@code HOGAR_YA_REGISTRADO} if the user already owns a household. */
    Membresia guardar(Membresia membresia);

    Optional<Membresia> buscar(UUID hogarId, UUID usuarioId);

    /** The user's memberships, oldest first. */
    List<Membresia> delUsuario(UUID usuarioId);

    /** The household's members, oldest first (the owner comes first). */
    List<Membresia> delHogar(UUID hogarId);

    boolean esTitularDeAlgunHogar(UUID usuarioId);

    void eliminar(Membresia membresia);
}
