package tech.tetengo.api.support;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;

/** Object storage for tests: uploads only "happen" when the test says so. */
public class AlmacenamientoDePrueba implements AlmacenamientoDeClips {

    public record Lectura(String clave, boolean descarga, String nombreArchivo) {}

    private final Set<String> pedidas = ConcurrentHashMap.newKeySet();
    private final Set<String> subidas = ConcurrentHashMap.newKeySet();
    private final List<String> eliminadas = new CopyOnWriteArrayList<>();
    private final List<Lectura> lecturas = new CopyOnWriteArrayList<>();

    @Override
    public URI urlDeSubida(String clave, Instant expiraEn) {
        pedidas.add(clave);
        return URI.create("https://s3.prueba/" + clave + "?metodo=PUT");
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn, boolean descarga, String nombreArchivo) {
        lecturas.add(new Lectura(clave, descarga, nombreArchivo));
        return URI.create(
                "https://s3.prueba/" + clave + "?metodo=GET" + (descarga ? "&descarga=" + nombreArchivo : ""));
    }

    @Override
    public boolean existe(String clave) {
        return subidas.contains(clave);
    }

    @Override
    public void eliminar(String clave) {
        subidas.remove(clave);
        eliminadas.add(clave);
    }

    /** The agent finished every upload it was given a URL for. */
    public void completarSubidas() {
        subidas.addAll(pedidas);
    }

    public Set<String> subidas() {
        return Set.copyOf(subidas);
    }

    public List<String> eliminadas() {
        return List.copyOf(eliminadas);
    }

    public List<Lectura> lecturas() {
        return List.copyOf(lecturas);
    }

    public void limpiar() {
        pedidas.clear();
        subidas.clear();
        eliminadas.clear();
        lecturas.clear();
    }
}
