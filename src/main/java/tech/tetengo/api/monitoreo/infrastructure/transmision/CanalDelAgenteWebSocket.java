package tech.tetengo.api.monitoreo.infrastructure.transmision;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agents' control WebSockets, one per camera; a new connection of the same camera replaces the
 * previous one. Text JSON only (AGENT_CONTRACT.md). In memory: the agent is connected to one API instance.
 */
@Component
class CanalDelAgenteWebSocket implements CanalDelAgente {

    private static final Logger log = LoggerFactory.getLogger(CanalDelAgenteWebSocket.class);
    private static final int ESPERA_DE_ENVIO_MS = 5_000;
    private static final int BUFER_MAXIMO = 64 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<UUID, WebSocketSession> agentes = new ConcurrentHashMap<>();

    void conectar(UUID camaraId, WebSocketSession sesion) {
        WebSocketSession anterior = agentes.put(
                camaraId, new ConcurrentWebSocketSessionDecorator(sesion, ESPERA_DE_ENVIO_MS, BUFER_MAXIMO));
        if (anterior != null && anterior.isOpen()) {
            try {
                anterior.close();
            } catch (IOException e) {
                log.debug("Canal del agente ya cerrado", e);
            }
        }
    }

    void desconectar(UUID camaraId, WebSocketSession sesion) {
        agentes.computeIfPresent(camaraId, (id, actual) -> mismaSesion(actual, sesion) ? null : actual);
    }

    @Override
    public void enviar(UUID camaraId, Mensaje mensaje) {
        WebSocketSession sesion = agentes.get(camaraId);
        if (sesion == null || !sesion.isOpen()) {
            log.debug("Agente de la cámara {} sin conectar; mensaje descartado", camaraId);
            return;
        }
        try {
            sesion.sendMessage(new TextMessage(json(mensaje)));
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo enviar al agente de la cámara {}", camaraId, e);
        }
    }

    /** The exact JSON of AGENT_CONTRACT.md, fields in that order. */
    static String json(Mensaje mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        switch (mensaje) {
            case Preparar p -> cuerpo.put("preparar", true);
            case Transmitir t -> {
                cuerpo.put("transmitir", true);
                cuerpo.put("urlPublicacion", t.urlPublicacion().toString());
                cuerpo.put("usuario", t.usuario());
                cuerpo.put("clave", t.clave());
                cuerpo.put("modo", t.modo().name());
            }
            case CambiarModo c -> cuerpo.put("modo", c.modo().name());
            case Detener d -> cuerpo.put("transmitir", false);
        }
        return JSON.writeValueAsString(cuerpo);
    }

    private static boolean mismaSesion(WebSocketSession guardada, WebSocketSession sesion) {
        WebSocketSession real = guardada instanceof ConcurrentWebSocketSessionDecorator d ? d.getDelegate() : guardada;
        return real == sesion || real.getId().equals(sesion.getId());
    }
}
