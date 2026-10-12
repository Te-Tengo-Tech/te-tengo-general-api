package tech.tetengo.api.shared.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;

/**
 * Fake adapter used when no bucket is configured ({@code tetengo.fotos.s3.bucket}). Its upload URLs
 * lead nowhere, so a photo can only be stored by code (tests); confirmations answer {@code 404
 * SUBIDA_NO_ENCONTRADA} otherwise.
 */
class AlmacenamientoDeFotosEnMemoria implements AlmacenamientoDeFotos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoDeFotosEnMemoria.class);
    private static final String BASE = "https://almacenamiento-falso.invalid/";

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

    @Override
    public Subida urlDeSubida(String clave, String contentType, long tamanoBytes, Instant expiraEn) {
        log.info("URL de subida falsa (sin tetengo.fotos.s3.bucket) para {}", clave);
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
        return Optional.ofNullable(objetos.get(clave)).map(bytes -> (long) bytes.length);
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        return Optional.ofNullable(objetos.get(clave)).map(byte[]::clone);
    }

    @Override
    public void guardar(String clave, byte[] contenido, String contentType) {
        objetos.put(clave, contenido.clone());
    }

    @Override
    public void eliminar(String clave) {
        objetos.remove(clave);
    }
}
