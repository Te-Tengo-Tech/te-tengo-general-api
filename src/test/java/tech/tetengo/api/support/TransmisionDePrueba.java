package tech.tetengo.api.support;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import tech.tetengo.api.monitoreo.application.port.ServicioDeTransmision;

/**
 * MediaMTX for tests: records which cameras and viewers were kicked out, and serves the HLS and WebRTC readers
 * the test declares. The real adapter has its own test against a MediaMTX container.
 */
public class TransmisionDePrueba implements ServicioDeTransmision {

    private final List<Lector> lectores = new CopyOnWriteArrayList<>();
    private final List<UUID> camarasExpulsadas = new CopyOnWriteArrayList<>();
    private final List<String> lectoresExpulsados = new CopyOnWriteArrayList<>();

    @Override
    public List<Lector> lectores() {
        return List.copyOf(lectores);
    }

    @Override
    public void expulsarCamara(UUID camaraId) {
        camarasExpulsadas.add(camaraId);
    }

    @Override
    public void expulsarLectores(Collection<String> huellasDeToken) {
        lectoresExpulsados.addAll(huellasDeToken);
    }

    /** An HLS viewer reading the camera with that token, having received {@code bytes} so far. */
    public void leyendo(String id, UUID camaraId, String huellaToken, long bytes) {
        leyendo(id, Lector.HLS, camaraId, huellaToken, bytes);
    }

    /** A viewer of that kind ({@link Lector#HLS} or {@link Lector#WEBRTC}) reading the camera. */
    public void leyendo(String id, String tipo, UUID camaraId, String huellaToken, long bytes) {
        lectores.removeIf(lector -> lector.id().equals(id));
        lectores.add(new Lector(id, tipo, camaraId, huellaToken, bytes));
    }

    public List<UUID> camarasExpulsadas() {
        return List.copyOf(camarasExpulsadas);
    }

    public List<String> lectoresExpulsados() {
        return List.copyOf(lectoresExpulsados);
    }

    public void limpiar() {
        lectores.clear();
        camarasExpulsadas.clear();
        lectoresExpulsados.clear();
    }
}
