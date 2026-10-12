package tech.tetengo.api.monitoreo.infrastructure.transmision;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tech.tetengo.api.monitoreo.application.ControlDeVistaEnVivo;
import tech.tetengo.api.shared.infrastructure.security.ClaimsDelToken;

/**
 * {@code /api/agente/transmision}: the household agent's control channel, authenticated with a
 * per-camera token (live view of that camera) or a linked PC's own access token (messages about the
 * PC, such as {@code desvincular}). Spring Security only lets {@code AGENTE} tokens reach it, and an
 * unlinked PC's tokens are rejected before the handshake ({@code 401 PC_DESVINCULADA}). The API sends
 * text JSON; video goes to MediaMTX, never through here.
 */
class ManejadorDelAgente extends TextWebSocketHandler implements HandshakeInterceptor {

    private static final String CAMARA = "camaraId";
    private static final String PC = "pcId";
    private static final String HOGAR = "hogarId";

    private final CanalDelAgenteWebSocket canal;
    private final ControlDeVistaEnVivo control;

    ManejadorDelAgente(CanalDelAgenteWebSocket canal, ControlDeVistaEnVivo control) {
        this.canal = canal;
        this.control = control;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest peticion,
            ServerHttpResponse respuesta,
            WebSocketHandler manejador,
            Map<String, Object> atributos) {
        if (!(peticion.getPrincipal() instanceof JwtAuthenticationToken token)
                || token.getToken().getClaimAsString(ClaimsDelToken.HOGAR) == null) {
            return false;
        }
        String camara = token.getToken().getClaimAsString(ClaimsDelToken.CAMARA);
        String pc = token.getToken().getClaimAsString(ClaimsDelToken.PC);
        if (camara == null && pc == null) {
            return false;
        }
        atributos.put(HOGAR, UUID.fromString(token.getToken().getClaimAsString(ClaimsDelToken.HOGAR)));
        if (camara != null) {
            atributos.put(CAMARA, UUID.fromString(camara));
        } else {
            atributos.put(PC, UUID.fromString(pc));
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest peticion, ServerHttpResponse respuesta, WebSocketHandler manejador, Exception error) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {
        UUID camara = (UUID) sesion.getAttributes().get(CAMARA);
        if (camara == null) {
            canal.conectar((UUID) sesion.getAttributes().get(PC), sesion);
            return;
        }
        canal.conectar(camara, sesion);
        control.alConectarseElAgente((UUID) sesion.getAttributes().get(HOGAR), camara);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        UUID canalId = (UUID) sesion.getAttributes()
                .getOrDefault(CAMARA, sesion.getAttributes().get(PC));
        canal.desconectar(canalId, sesion);
    }
}
