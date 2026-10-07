package tech.tetengo.api.monitoreo.infrastructure.transmision;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tech.tetengo.api.shared.infrastructure.security.ClaimsDelToken;

/**
 * {@code /api/agente/transmision}: the household agent's stream, authenticated with its per-camera
 * token (Spring Security only lets {@code AGENTE} tokens reach it).
 */
class ManejadorDelAgente extends BinaryWebSocketHandler implements HandshakeInterceptor {

    private static final String CAMARA = "camaraId";

    private final RelevoDeVistaEnVivo relevo;

    ManejadorDelAgente(RelevoDeVistaEnVivo relevo) {
        this.relevo = relevo;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest peticion,
            ServerHttpResponse respuesta,
            WebSocketHandler manejador,
            Map<String, Object> atributos) {
        if (peticion.getPrincipal() instanceof JwtAuthenticationToken token
                && token.getToken().getClaimAsString(ClaimsDelToken.CAMARA) != null) {
            atributos.put(CAMARA, UUID.fromString(token.getToken().getClaimAsString(ClaimsDelToken.CAMARA)));
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest peticion, ServerHttpResponse respuesta, WebSocketHandler manejador, Exception error) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {
        relevo.conectarAgente(camara(sesion), sesion);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession sesion, BinaryMessage cuadro) {
        relevo.reenviar(camara(sesion), cuadro);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        relevo.desconectarAgente(camara(sesion), sesion);
    }

    private static UUID camara(WebSocketSession sesion) {
        return (UUID) sesion.getAttributes().get(CAMARA);
    }
}
