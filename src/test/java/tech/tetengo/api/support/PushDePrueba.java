package tech.tetengo.api.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.tetengo.api.shared.application.port.NotificadorPush;
import tech.tetengo.api.shared.application.port.TipoAviso;

/** Records push notices instead of sending them; can simulate an unresponsive push service. */
public class PushDePrueba implements NotificadorPush {

    public record Envio(List<Destino> destinos, Aviso aviso) {

        public List<String> tokens() {
            return destinos.stream().map(Destino::tokenPush).toList();
        }
    }

    private final List<Envio> enviados = new CopyOnWriteArrayList<>();
    private final AtomicBoolean caido = new AtomicBoolean();

    @Override
    public void enviar(List<Destino> destinos, Aviso aviso) {
        if (caido.get()) {
            throw new FallaDePush("Servicio de push de prueba sin respuesta", null);
        }
        enviados.add(new Envio(List.copyOf(destinos), aviso));
    }

    public List<Envio> enviados() {
        return List.copyOf(enviados);
    }

    public List<Envio> deTipo(TipoAviso tipo) {
        return enviados.stream().filter(e -> e.aviso().tipo() == tipo).toList();
    }

    /** CA-16.4: the push service does not respond while this is on. */
    public void simularCaida(boolean caido) {
        this.caido.set(caido);
    }

    public void limpiar() {
        enviados.clear();
        caido.set(false);
    }
}
