package tech.tetengo.api.camaras.application.port;

import java.time.Instant;
import java.util.Optional;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo;

/** PC link requests (global table). */
public interface AutorizacionDeDispositivoRepository {

    AutorizacionDeDispositivo guardar(AutorizacionDeDispositivo autorizacion);

    /** By the hash of the device code, locking the row (the PC may poll twice at once). */
    Optional<AutorizacionDeDispositivo> porCodigoDeDispositivoParaActualizar(String huella);

    /** By the hash of the device code, without locking. */
    Optional<AutorizacionDeDispositivo> porCodigoDeDispositivo(String huella);

    /** The newest request with that user code, locking the row. */
    Optional<AutorizacionDeDispositivo> porCodigoDeUsuarioParaActualizar(String huella);

    /** Whether a request still pending and not expired uses that user code. */
    boolean codigoDeUsuarioEnUso(String huella, Instant ahora);

    /** Deletes the requests that expired before {@code limite}; returns how many. */
    int eliminarVencidasAntesDe(Instant limite);
}
