package tech.tetengo.api.hogares.application.port;

import java.util.Optional;
import tech.tetengo.api.hogares.domain.model.Consentimiento;

/** Household data: queries are filtered by the household of the request. */
public interface ConsentimientoRepository {

    Consentimiento guardar(Consentimiento consentimiento);

    /** The most recent consent of the household, current or not. */
    Optional<Consentimiento> ultimo();
}
