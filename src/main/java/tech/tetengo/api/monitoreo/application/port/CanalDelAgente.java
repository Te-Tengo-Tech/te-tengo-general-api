package tech.tetengo.api.monitoreo.application.port;

import java.net.URI;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;

/**
 * The control channel to the household agent of a camera: the WebSocket {@code /api/agente/transmision}
 * (AGENT_CONTRACT.md). A message to an agent that is not connected is dropped: the agent gets the
 * current state when it connects.
 */
public interface CanalDelAgente {

    void enviar(UUID camaraId, Mensaje mensaje);

    sealed interface Mensaje permits Transmitir, CambiarModo, Detener {}

    /** {@code {"transmitir":true,"urlPublicacion":…,"usuario":"agente","clave":…,"modo":…}}. */
    record Transmitir(URI urlPublicacion, String usuario, String clave, ModoDeVista modo) implements Mensaje {}

    /** {@code {"modo":…}} during a transmission. */
    record CambiarModo(ModoDeVista modo) implements Mensaje {}

    /** {@code {"transmitir":false}}. */
    record Detener() implements Mensaje {}
}
