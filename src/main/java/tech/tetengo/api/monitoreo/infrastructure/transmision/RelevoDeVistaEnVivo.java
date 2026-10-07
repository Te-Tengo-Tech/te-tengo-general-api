package tech.tetengo.api.monitoreo.infrastructure.transmision;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Live view relay (API contract §4, proposal pending team confirmation). The household agent keeps a
 * WebSocket per camera; it is told to stream ({@code {"transmitir":true}}) while at least one viewer
 * is connected, and each binary frame it sends ({@code [8-byte big-endian ms timestamp][JPEG]}) is
 * forwarded as is to the camera's viewers. In memory: one API instance.
 */
@Component
public class RelevoDeVistaEnVivo {

    static final TextMessage TRANSMITIR = new TextMessage("{\"transmitir\":true}");
    static final TextMessage DETENER = new TextMessage("{\"transmitir\":false}");

    private static final Logger log = LoggerFactory.getLogger(RelevoDeVistaEnVivo.class);
    private static final int ESPERA_DE_ENVIO_MS = 5_000;
    private static final int BUFER_MAXIMO = 2 * 1024 * 1024;

    private final Map<UUID, WebSocketSession> agentes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<WebSocketSession>> espectadores = new ConcurrentHashMap<>();

    public void conectarAgente(UUID camaraId, WebSocketSession sesion) {
        WebSocketSession anterior = agentes.put(camaraId, envolver(sesion));
        cerrarSinError(anterior);
        if (!espectadoresDe(camaraId).isEmpty()) {
            enviar(agentes.get(camaraId), TRANSMITIR);
        }
    }

    public void desconectarAgente(UUID camaraId, WebSocketSession sesion) {
        agentes.computeIfPresent(camaraId, (id, actual) -> mismaSesion(actual, sesion) ? null : actual);
    }

    public void conectarEspectador(UUID camaraId, WebSocketSession sesion) {
        Set<WebSocketSession> conectados = espectadores.computeIfAbsent(camaraId, id -> ConcurrentHashMap.newKeySet());
        boolean primero = conectados.isEmpty();
        conectados.add(envolver(sesion));
        if (primero) {
            enviar(agentes.get(camaraId), TRANSMITIR);
        }
    }

    public void desconectarEspectador(UUID camaraId, WebSocketSession sesion) {
        Set<WebSocketSession> conectados = espectadoresDe(camaraId);
        conectados.removeIf(s -> mismaSesion(s, sesion));
        if (conectados.isEmpty()) {
            espectadores.remove(camaraId);
            enviar(agentes.get(camaraId), DETENER);
        }
    }

    /** A frame from the camera's agent goes to every viewer of that camera. */
    public void reenviar(UUID camaraId, BinaryMessage cuadro) {
        espectadoresDe(camaraId).forEach(espectador -> enviar(espectador, cuadro));
    }

    int espectadores(UUID camaraId) {
        return espectadoresDe(camaraId).size();
    }

    private Set<WebSocketSession> espectadoresDe(UUID camaraId) {
        return espectadores.getOrDefault(camaraId, Set.of());
    }

    private static WebSocketSession envolver(WebSocketSession sesion) {
        return sesion instanceof ConcurrentWebSocketSessionDecorator
                ? sesion
                : new ConcurrentWebSocketSessionDecorator(sesion, ESPERA_DE_ENVIO_MS, BUFER_MAXIMO);
    }

    private static boolean mismaSesion(WebSocketSession guardada, WebSocketSession sesion) {
        WebSocketSession real = guardada instanceof ConcurrentWebSocketSessionDecorator d ? d.getDelegate() : guardada;
        return real == sesion || guardada == sesion || real.getId().equals(sesion.getId());
    }

    private static void enviar(WebSocketSession sesion, WebSocketMessage<?> mensaje) {
        if (sesion == null || !sesion.isOpen()) {
            return;
        }
        try {
            sesion.sendMessage(mensaje);
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo enviar a la sesión de transmisión {}", sesion.getId(), e);
        }
    }

    private static void cerrarSinError(WebSocketSession sesion) {
        if (sesion != null && sesion.isOpen()) {
            try {
                sesion.close();
            } catch (IOException e) {
                log.debug("Sesión de transmisión ya cerrada", e);
            }
        }
    }
}
