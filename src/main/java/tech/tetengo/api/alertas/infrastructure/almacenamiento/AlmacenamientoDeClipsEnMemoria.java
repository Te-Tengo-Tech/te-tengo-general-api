package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;

/**
 * Fake adapter until Amazon S3 is configured (see {@code docs/BLOCKERS.md}). It hands out
 * placeholder URLs and treats a clip as uploaded once its upload URL was issued, so the app flow can
 * be tried locally.
 */
@Component
class AlmacenamientoDeClipsEnMemoria implements AlmacenamientoDeClips {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoDeClipsEnMemoria.class);
    private static final String BASE = "https://almacenamiento-falso.invalid/clips/";

    private final Set<String> claves = ConcurrentHashMap.newKeySet();

    @Override
    public Subida urlDeSubida(String clave, String contentType, Instant expiraEn) {
        claves.add(clave);
        log.info("URL de subida falsa (falta Amazon S3) para {}", clave);
        return new Subida(
                URI.create(BASE + clave + "?subida&expira=" + expiraEn.getEpochSecond()),
                Map.of("Content-Type", contentType));
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn, boolean descarga, String nombreArchivo) {
        return URI.create(BASE + clave + "?expira=" + expiraEn.getEpochSecond() + (descarga ? "&descarga" : ""));
    }

    @Override
    public boolean existe(String clave) {
        return claves.contains(clave);
    }

    @Override
    public void eliminar(String clave) {
        claves.remove(clave);
    }
}
