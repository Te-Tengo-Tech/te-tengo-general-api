package tech.tetengo.api.monitoreo.infrastructure.transmision;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tech.tetengo.api.monitoreo.application.ConexionDeTransmision;

/** WebSocket endpoints of the live view relay (US-23). Access is by token, not by origin. */
@Configuration
@EnableWebSocket
class TransmisionConfig implements WebSocketConfigurer {

    private final RelevoDeVistaEnVivo relevo;
    private final ConexionDeTransmision conexion;

    TransmisionConfig(RelevoDeVistaEnVivo relevo, ConexionDeTransmision conexion) {
        this.relevo = relevo;
        this.conexion = conexion;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registro) {
        ManejadorDelAgente agente = new ManejadorDelAgente(relevo);
        ManejadorDelEspectador espectador = new ManejadorDelEspectador(relevo, conexion);
        registro.addHandler(agente, "/api/agente/transmision")
                .addInterceptors(agente)
                .setAllowedOriginPatterns("*");
        registro.addHandler(espectador, "/api/vista-en-vivo/*/transmision")
                .addInterceptors(espectador)
                .setAllowedOriginPatterns("*");
    }
}
