package tech.tetengo.api.versiones.application.port;

import java.net.URI;
import tech.tetengo.api.versiones.domain.model.EntradaDeManifiesto;

/** Reads the manifest of one channel that a release workflow published (ADR 0012). */
public interface FuenteDeManifiestos {

    Lectura leer(URI manifiesto);

    /** What reading a manifest gave. */
    sealed interface Lectura {

        /** The manifest, not checked yet. */
        record Publicada(EntradaDeManifiesto entrada) implements Lectura {}

        /** There is no manifest ({@code 404}): nothing was published on that channel. */
        record SinPublicar() implements Lectura {}

        /** It could not be read (network, server error, invalid JSON): the last read stays. */
        record Fallida() implements Lectura {}
    }
}
