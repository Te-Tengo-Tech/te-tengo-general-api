package tech.tetengo.api.shared.application.port;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Object storage of the profile photos (API contract §13): the same store as the clips (S3 or
 * Cloudflare R2). The app uploads through a pre-signed PUT; the backend reads the upload, stores the
 * normalized JPEG and hands out pre-signed GET URLs.
 */
public interface AlmacenamientoDeFotos {

    /** A stored photo is read through a pre-signed GET valid this long (API contract §13). */
    Duration VIGENCIA_DE_LECTURA = Duration.ofHours(1);

    /**
     * Pre-signed PUT for exactly this type and size, and the headers the client must send. The size is
     * signed too, but {@code Content-Length} is left out of the headers: every HTTP client sets it.
     */
    Subida urlDeSubida(String clave, String contentType, long tamanoBytes, Instant expiraEn);

    URI urlDeLectura(String clave, Instant expiraEn);

    /** Size of the stored object, empty if it does not exist. */
    Optional<Long> tamano(String clave);

    /** The object's bytes, empty if it does not exist. */
    Optional<byte[]> leer(String clave);

    void guardar(String clave, byte[] contenido, String contentType);

    /** Deleting a missing object is not an error. */
    void eliminar(String clave);

    /** The photo stored under {@code clave}, readable for one hour from {@code ahora}. */
    default Foto foto(String clave, Instant ahora) {
        Instant expiraEn = ahora.plus(VIGENCIA_DE_LECTURA);
        return new Foto(urlDeLectura(clave, expiraEn), expiraEn);
    }

    /** {@code Foto} of the API contract. */
    record Foto(URI url, Instant expiraEn) {}

    /** A pre-signed upload: {@code PUT url} with exactly these {@code cabeceras}. */
    record Subida(URI url, Map<String, String> cabeceras) {

        public Subida {
            cabeceras = Map.copyOf(cabeceras);
        }
    }
}
