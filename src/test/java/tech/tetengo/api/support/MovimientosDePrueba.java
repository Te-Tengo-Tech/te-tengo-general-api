package tech.tetengo.api.support;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;

/** Object storage of the movement files for tests: an upload only "happens" when the test says so. */
public class MovimientosDePrueba implements AlmacenamientoDeMovimientos {

    private final Set<String> pedidas = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> subidas = new ConcurrentHashMap<>();
    private final List<String> eliminadas = new CopyOnWriteArrayList<>();

    @Override
    public Subida urlDeSubida(String clave, String contentType, Instant expiraEn) {
        pedidas.add(clave);
        return new Subida(
                URI.create("https://r2.prueba/" + clave + "?metodo=PUT"), Map.of("Content-Type", contentType));
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn) {
        return URI.create("https://r2.prueba/" + clave + "?metodo=GET");
    }

    @Override
    public Optional<Long> tamano(String clave) {
        return Optional.ofNullable(subidas.get(clave));
    }

    @Override
    public void eliminar(Collection<String> claves) {
        claves.forEach(clave -> {
            subidas.remove(clave);
            eliminadas.add(clave);
        });
    }

    /** The PC finished the PUT of that object. */
    public void subir(String clave, long tamano) {
        subidas.put(clave, tamano);
    }

    public Set<String> pedidas() {
        return Set.copyOf(pedidas);
    }

    public Set<String> subidas() {
        return Set.copyOf(subidas.keySet());
    }

    public List<String> eliminadas() {
        return List.copyOf(eliminadas);
    }

    public void limpiar() {
        pedidas.clear();
        subidas.clear();
        eliminadas.clear();
    }
}
