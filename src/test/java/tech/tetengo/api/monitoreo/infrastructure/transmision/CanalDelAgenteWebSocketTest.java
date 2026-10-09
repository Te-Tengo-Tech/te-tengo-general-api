package tech.tetengo.api.monitoreo.infrastructure.transmision;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.CambiarModo;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Detener;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Preparar;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Transmitir;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;

/** The control messages are exactly the JSON of AGENT_CONTRACT.md. */
class CanalDelAgenteWebSocketTest {

    @Test
    void transmitirLlevaLaUrlLasCredencialesYElModo() {
        assertThat(CanalDelAgenteWebSocket.json(new Transmitir(
                        URI.create("rtsp://localhost:8554/camaras/c1"), "agente", "k", ModoDeVista.VIDEO)))
                .isEqualTo(
                        "{\"transmitir\":true,\"urlPublicacion\":\"rtsp://localhost:8554/camaras/c1\",\"usuario\":\"agente\",\"clave\":\"k\",\"modo\":\"VIDEO\"}");
    }

    @Test
    void cambiarModoYDetener() {
        assertThat(CanalDelAgenteWebSocket.json(new CambiarModo(ModoDeVista.VIDEO_CON_POSTURA)))
                .isEqualTo("{\"modo\":\"VIDEO_CON_POSTURA\"}");
        assertThat(CanalDelAgenteWebSocket.json(new Detener())).isEqualTo("{\"transmitir\":false}");
    }

    @Test
    void prepararNoLlevaModoNiCredenciales() {
        // No "modo" key: an agent that does not know "preparar" must not read it as a mode change.
        assertThat(CanalDelAgenteWebSocket.json(new Preparar())).isEqualTo("{\"preparar\":true}");
    }
}
