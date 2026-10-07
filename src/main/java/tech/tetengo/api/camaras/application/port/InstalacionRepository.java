package tech.tetengo.api.camaras.application.port;

import java.util.Optional;
import tech.tetengo.api.camaras.domain.model.Instalacion;

/** Installations are global: they are looked up by the hash of the installation credential. */
public interface InstalacionRepository {

    Optional<Instalacion> buscarPorCredencial(String huella);

    /** Same lookup, locking the row until the transaction ends (concurrent registrations). */
    Optional<Instalacion> buscarPorCredencialParaActualizar(String huella);

    Instalacion guardar(Instalacion instalacion);
}
