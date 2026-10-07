package tech.tetengo.api.hogares.application.port;

import java.util.Optional;
import tech.tetengo.api.hogares.domain.model.Invitacion;

/** Invitations are global: they are looked up by the hash of the link token. */
public interface InvitacionRepository {

    Invitacion guardar(Invitacion invitacion);

    Optional<Invitacion> buscarPorToken(String huella);
}
