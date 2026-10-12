package tech.tetengo.api.support;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;

/** Object storage of the photos for tests: an upload only "happens" when the test puts its bytes. */
public class FotosDePrueba implements AlmacenamientoDeFotos {

    public record Pedida(String clave, String tipo, long tamanoBytes, Instant expiraEn) {}

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
    private final List<Pedida> pedidas = new CopyOnWriteArrayList<>();
    private final List<String> eliminadas = new CopyOnWriteArrayList<>();

    @Override
    public Subida urlDeSubida(String clave, String contentType, long tamanoBytes, Instant expiraEn) {
        pedidas.add(new Pedida(clave, contentType, tamanoBytes, expiraEn));
        return new Subida(
                URI.create("https://r2.prueba/" + clave + "?metodo=PUT"), Map.of("Content-Type", contentType));
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn) {
        return URI.create("https://r2.prueba/" + clave + "?metodo=GET&expira=" + expiraEn.getEpochSecond());
    }

    @Override
    public Optional<Long> tamano(String clave) {
        return Optional.ofNullable(objetos.get(clave)).map(b -> (long) b.length);
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        return Optional.ofNullable(objetos.get(clave));
    }

    @Override
    public void guardar(String clave, byte[] contenido, String contentType) {
        objetos.put(clave, contenido);
    }

    @Override
    public void eliminar(String clave) {
        objetos.remove(clave);
        eliminadas.add(clave);
    }

    /** The app uploaded these bytes through the pre-signed PUT. */
    public void subir(String clave, byte[] contenido) {
        objetos.put(clave, contenido);
    }

    public Optional<byte[]> objeto(String clave) {
        return Optional.ofNullable(objetos.get(clave));
    }

    public Set<String> claves() {
        return Set.copyOf(objetos.keySet());
    }

    public List<Pedida> pedidas() {
        return List.copyOf(pedidas);
    }

    public List<String> eliminadas() {
        return List.copyOf(eliminadas);
    }

    public void limpiar() {
        objetos.clear();
        pedidas.clear();
        eliminadas.clear();
    }
}
