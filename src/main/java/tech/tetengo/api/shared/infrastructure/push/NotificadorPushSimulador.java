package tech.tetengo.api.shared.infrastructure.push;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tetengo.api.shared.application.port.NotificadorPush;
import tools.jackson.databind.json.JsonMapper;

/**
 * Local push to the iOS simulator: {@code xcrun simctl push <device> <bundle-id> -} with the APNs
 * payload of the other providers, including {@code gcm.message_id}, so the app (FlutterFire) handles
 * it as an FCM message and routes on the same data keys. Every notice goes once to the simulator,
 * whatever the registered tokens. Without {@code xcrun} (CI, Linux) it only logs a warning; when
 * {@code simctl} fails (e.g. no booted simulator) it throws {@link FallaDePush}, so the notice is
 * retried like with any other provider (CA-16.4).
 */
class NotificadorPushSimulador implements NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPushSimulador.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Runs a command with the given standard input. */
    interface Ejecutor {
        Salida ejecutar(List<String> comando, String entrada) throws IOException, InterruptedException;
    }

    record Salida(int codigo, String texto) {}

    private final PropiedadesDelSimulador propiedades;
    private final Ejecutor ejecutor;
    private final AtomicBoolean sinXcrunAvisado = new AtomicBoolean();

    NotificadorPushSimulador(PropiedadesDelSimulador propiedades, Ejecutor ejecutor) {
        this.propiedades = propiedades;
        this.ejecutor = ejecutor;
    }

    NotificadorPushSimulador(PropiedadesDelSimulador propiedades) {
        this(propiedades, NotificadorPushSimulador::proceso);
    }

    @Override
    public Resultado enviar(List<Destino> destinos, Aviso aviso) {
        String carga = carga(ContenidoDelAviso.de(aviso), UUID.randomUUID().toString());
        List<String> comando =
                List.of("xcrun", "simctl", "push", propiedades.dispositivo(), propiedades.bundleId(), "-");
        Salida salida;
        try {
            salida = ejecutor.ejecutar(comando, carga);
        } catch (IOException e) {
            if (!sinXcrunAvisado.getAndSet(true)) {
                log.warn("Push al simulador desactivado: no se encontró xcrun ({})", e.getMessage());
            }
            log.info("Push {} (sin enviar, falta xcrun): {}", aviso.tipo(), carga);
            return Resultado.aceptados(destinos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FallaDePush("Se interrumpió el push al simulador", e);
        }
        if (salida.codigo() != 0) {
            throw new FallaDePush(
                    "simctl no aceptó el push " + aviso.tipo() + " (" + salida.codigo() + "): " + salida.texto(), null);
        }
        log.info("Push {} enviado al simulador {}", aviso.tipo(), propiedades.dispositivo());
        return Resultado.aceptados(destinos);
    }

    static String carga(ContenidoDelAviso contenido, String idDelMensaje) {
        return JSON.writeValueAsString(CargasPush.apns(contenido, idDelMensaje));
    }

    private static Salida proceso(List<String> comando, String entrada) throws IOException, InterruptedException {
        Process proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
        try (OutputStream stdin = proceso.getOutputStream()) {
            stdin.write(entrada.getBytes(StandardCharsets.UTF_8));
        }
        if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
            proceso.destroyForcibly();
            return new Salida(-1, "simctl no respondió en 10 s");
        }
        String texto = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        return new Salida(proceso.exitValue(), texto);
    }
}
