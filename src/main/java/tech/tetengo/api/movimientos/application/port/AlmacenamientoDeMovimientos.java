package tech.tetengo.api.movimientos.application.port;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Object storage of the movement files (Cloudflare R2 through its S3 API, or Amazon S3; ADR 0010).
 * The PC uploads and the app reads with short-lived pre-signed URLs: the bytes never pass through the
 * API.
 */
public interface AlmacenamientoDeMovimientos {

    /** Pre-signed PUT URL and the headers the PUT must carry. */
    Subida urlDeSubida(String clave, String contentType, Instant expiraEn);

    /** Pre-signed GET URL. */
    URI urlDeLectura(String clave, Instant expiraEn);

    /** The object's size, if it exists. */
    Optional<Long> tamano(String clave);

    /** Deletes the objects; missing ones are ignored. */
    void eliminar(Collection<String> claves);

    record Subida(URI url, Map<String, String> cabeceras) {

        public Subida {
            cabeceras = Map.copyOf(cabeceras);
        }
    }
}
