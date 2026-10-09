package tech.tetengo.api.hogares;

import java.util.List;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.Rol;

/** Public API of {@code hogares}: who belongs to a household, e.g. to deliver push notices. */
public interface MiembrosDelHogar {

    /** Members of the household, owner first. */
    List<Miembro> de(UUID hogarId);

    record Miembro(UUID usuarioId, Rol rol) {}
}
