package tech.tetengo.api.monitoreo.infrastructure.transmision;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tech.tetengo.api.monitoreo.application.ControlDeVistaEnVivo;

/** The agent's live view control channel (US-23). Access is by the agent's token, not by origin. */
@Configuration
@EnableWebSocket
class TransmisionConfig implements WebSocketConfigurer {

    private final CanalDelAgenteWebSocket canal;
    private final ControlDeVistaEnVivo control;

    TransmisionConfig(CanalDelAgenteWebSocket canal, ControlDeVistaEnVivo control) {
        this.canal = canal;
        this.control = control;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registro) {
        ManejadorDelAgente agente = new ManejadorDelAgente(canal, control);
        registro.addHandler(agente, "/api/agente/transmision")
                .addInterceptors(agente)
                .setAllowedOriginPatterns("*");
    }
}
