package tech.tetengo.api.support;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import tech.tetengo.api.shared.application.port.NotificadorPush;
import tech.tetengo.api.shared.application.port.TipoAviso;

/**
 * Records push notices instead of sending them; can simulate an unresponsive push service and
 * rejected tokens. Like the SNS adapter, it reports a provider address for every device without one.
 * Notices go out after the commit, on another thread (the outbox of {@code EnvioDeAvisos}), so reading
 * what was sent first waits for those first attempts ({@link #antesDeLeer}).
 */
public class PushDePrueba implements NotificadorPush {

    /** {@code hilo}: the thread that sent it. */
    public record Envio(List<Destino> destinos, Aviso aviso, String hilo) {

        public List<String> tokens() {
            return destinos.stream().map(Destino::tokenPush).toList();
        }
    }

    private final List<Envio> enviados = new CopyOnWriteArrayList<>();
    private final AtomicBoolean caido = new AtomicBoolean();
    private final Set<String> invalidos = ConcurrentHashMap.newKeySet();
    private volatile Runnable antesDeLeer = () -> {};

    /** Runs before every read of the sent notices: the tests wait for pending deliveries. */
    public void antesDeLeer(Runnable espera) {
        this.antesDeLeer = espera;
    }

    @Override
    public Resultado enviar(List<Destino> destinos, Aviso aviso) {
        if (caido.get()) {
            throw new FallaDePush("Servicio de push de prueba sin respuesta", null);
        }
        enviados.add(
                new Envio(List.copyOf(destinos), aviso, Thread.currentThread().getName()));
        Set<String> rechazados = destinos.stream()
                .map(Destino::tokenPush)
                .filter(invalidos::contains)
                .collect(Collectors.toSet());
        Map<String, String> referencias = destinos.stream()
                .filter(d -> d.referencia() == null && !rechazados.contains(d.tokenPush()))
                .collect(Collectors.toMap(Destino::tokenPush, d -> "referencia-" + d.tokenPush()));
        return new Resultado(destinos.size() - rechazados.size(), rechazados, referencias);
    }

    /** The service reports the token as no longer valid (e.g. FCM {@code UNREGISTERED}). */
    public void rechazarToken(String tokenPush) {
        invalidos.add(tokenPush);
    }

    public List<Envio> enviados() {
        antesDeLeer.run();
        return List.copyOf(enviados);
    }

    public List<Envio> deTipo(TipoAviso tipo) {
        antesDeLeer.run();
        return enviados.stream().filter(e -> e.aviso().tipo() == tipo).toList();
    }

    /** CA-16.4: the push service does not respond while this is on. */
    public void simularCaida(boolean caido) {
        this.caido.set(caido);
    }

    /** Forgets what was sent so far, after waiting for the deliveries still under way. */
    public void limpiar() {
        antesDeLeer.run();
        enviados.clear();
        caido.set(false);
        invalidos.clear();
    }
}
