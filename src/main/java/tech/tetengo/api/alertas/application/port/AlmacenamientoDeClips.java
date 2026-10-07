package tech.tetengo.api.alertas.application.port;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Object storage of the clips (Amazon S3 or an S3-compatible store). Clips are only reached through
 * short-lived pre-signed URLs.
 */
public interface AlmacenamientoDeClips {

    /**
     * Pre-signed PUT URL for the household agent to upload the clip, and the headers the PUT must
     * carry (they are part of the signature).
     */
    Subida urlDeSubida(String clave, String contentType, Instant expiraEn);

    /**
     * Pre-signed GET URL for the app. With {@code descarga}, the response asks the browser to save
     * the file with the given name (CA-26.2).
     */
    URI urlDeLectura(String clave, Instant expiraEn, boolean descarga, String nombreArchivo);

    boolean existe(String clave);

    void eliminar(String clave);

    /** A pre-signed upload: {@code PUT url} with exactly these {@code cabeceras}. */
    record Subida(URI url, Map<String, String> cabeceras) {

        public Subida {
            cabeceras = Map.copyOf(cabeceras);
        }
    }
}
