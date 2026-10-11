package tech.tetengo.api.movimientos.application.port;

import java.util.Optional;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;

/** Household data. */
public interface ConsentimientoDeMovimientosRepository {

    ConsentimientoDeMovimientos guardar(ConsentimientoDeMovimientos consentimiento);

    /** The latest one, in force or not. */
    Optional<ConsentimientoDeMovimientos> ultimo();
}
