package tech.tetengo.api.monitoreo.infrastructure.transmision;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import tech.tetengo.api.monitoreo.application.ConexionDeTransmision;
import tech.tetengo.api.monitoreo.application.ConexionDeTransmision.Espectador;

/**
 * {@code /api/vista-en-vivo/{sesionId}/transmision?token=…}: the app's side of the stream. The one-time
 * token of the URL authenticates it; closing the connection ends the session (CA-24.1).
 */
class ManejadorDelEspectador extends AbstractWebSocketHandler implements HandshakeInterceptor {

    private static final String ESPECTADOR = "espectador";

    private final RelevoDeVistaEnVivo relevo;
    private final ConexionDeTransmision conexion;

    ManejadorDelEspectador(RelevoDeVistaEnVivo relevo, ConexionDeTransmision conexion) {
        this.relevo = relevo;
        this.conexion = conexion;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest peticion,
            ServerHttpResponse respuesta,
            WebSocketHandler manejador,
            Map<String, Object> atributos) {
        Optional<Espectador> espectador = sesionDe(peticion)
                .flatMap(sesion -> tokenDe(peticion).flatMap(token -> conexion.conectar(sesion, token)));
        if (espectador.isEmpty()) {
            respuesta.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        atributos.put(ESPECTADOR, espectador.get());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest peticion, ServerHttpResponse respuesta, WebSocketHandler manejador, Exception error) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {
        relevo.conectarEspectador(espectador(sesion).camaraId(), sesion);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        Espectador espectador = espectador(sesion);
        relevo.desconectarEspectador(espectador.camaraId(), sesion);
        conexion.desconectar(espectador);
    }

    private static Espectador espectador(WebSocketSession sesion) {
        return (Espectador) sesion.getAttributes().get(ESPECTADOR);
    }

    private static Optional<UUID> sesionDe(ServerHttpRequest peticion) {
        String[] partes = peticion.getURI().getPath().split("/");
        // /api/vista-en-vivo/{sesionId}/transmision
        try {
            return Optional.of(UUID.fromString(partes[partes.length - 2]));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> tokenDe(ServerHttpRequest peticion) {
        return Optional.ofNullable(UriComponentsBuilder.fromUri(peticion.getURI())
                .build()
                .getQueryParams()
                .getFirst("token"));
    }
}
