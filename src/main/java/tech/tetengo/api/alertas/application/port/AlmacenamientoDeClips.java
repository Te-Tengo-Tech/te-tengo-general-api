package tech.tetengo.api.alertas.application.port;

import java.net.URI;
import java.time.Instant;

/**
 * Object storage of the clips (Amazon S3 in production, see {@code docs/BLOCKERS.md}). Clips are
 * only reached through short-lived pre-signed URLs.
 */
public interface AlmacenamientoDeClips {

    /** Pre-signed PUT URL for the household agent to upload the clip. */
    URI urlDeSubida(String clave, Instant expiraEn);

    /**
     * Pre-signed GET URL for the app. With {@code descarga}, the response asks the browser to save
     * the file with the given name (CA-26.2).
     */
    URI urlDeLectura(String clave, Instant expiraEn, boolean descarga, String nombreArchivo);

    boolean existe(String clave);

    void eliminar(String clave);
}
