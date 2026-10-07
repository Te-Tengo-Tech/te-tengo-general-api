package tech.tetengo.api.alertas.application.port;

import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.Alerta;

/** Household data: queries are filtered by the household in context. */
public interface AlertaRepository {

    Alerta guardar(Alerta alerta);

    Optional<Alerta> buscar(UUID id);

    /** The most recent active unstable-movement alert of the camera (CA-17.3). */
    Optional<Alerta> inestableActivaDe(UUID camaraId);

    /** The most recent active, unconfirmed fall of the camera (CA-13.1). */
    Optional<Alerta> caidaPorConfirmarDe(UUID camaraId);

    /** The most recent fall of the camera the person has not got up from yet (CA-13.2). */
    Optional<Alerta> caidaSinRecuperacionDe(UUID camaraId);
}
