package tech.tetengo.api.movimientos.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;

/**
 * Fake used without a bucket: placeholder URLs, and a file counts as uploaded once its URL was issued,
 * so the flow can be tried locally.
 */
class AlmacenamientoDeMovimientosEnMemoria implements AlmacenamientoDeMovimientos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoDeMovimientosEnMemoria.class);
    private static final String BASE = "https://almacenamiento-falso.invalid/";

    private final Map<String, Long> objetos = new ConcurrentHashMap<>();

    @Override
    public Subida urlDeSubida(String clave, String contentType, Instant expiraEn) {
        objetos.put(clave, 0L);
        log.info("URL de subida falsa (sin bucket de movimientos) para {}", clave);
        return new Subida(
                URI.create(BASE + clave + "?subida&expira=" + expiraEn.getEpochSecond()),
                Map.of("Content-Type", contentType));
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn) {
        return URI.create(BASE + clave + "?expira=" + expiraEn.getEpochSecond());
    }

    @Override
    public Optional<Long> tamano(String clave) {
        return Optional.ofNullable(objetos.get(clave));
    }

    @Override
    public void eliminar(Collection<String> claves) {
        claves.forEach(objetos::remove);
    }
}
