package tech.tetengo.api.monitoreo.infrastructure.transmision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.VinculacionDePrueba;

/**
 * US-31 / CA-31.1 over a real WebSocket: an online PC hears at once that it was removed from the
 * app, on its own control channel and on its cameras' channels, which the API then closes.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location=",
            "tetengo.tareas.habilitadas=false",
            AbstractIntegrationTest.SECRETO_MEDIAMTX
        })
class DesvinculacionPorElCanalIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int puerto;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String sesion;
    String titular;

    static class Receptor extends TextWebSocketHandler {
        final List<String> textos = new CopyOnWriteArrayList<>();
        final AtomicReference<CloseStatus> cierre = new AtomicReference<>();

        @Override
        protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
            textos.add(mensaje.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
            cierre.set(estado);
        }
    }

    @BeforeEach
    void titular() throws Exception {
        sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
    }

    private WebSocketSession conectar(String token, Receptor receptor) throws Exception {
        var cabeceras = new WebSocketHttpHeaders();
        cabeceras.add("Authorization", bearer(token));
        return new StandardWebSocketClient()
                .execute(receptor, cabeceras, URI.create("ws://localhost:" + puerto + "/api/agente/transmision"))
                .get();
    }

    @Test
    void ca31_1_laPcEnLineaRecibeDesvincularPorSuCanalYElCanalSeCierra() throws Exception {
        String tokens = VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa");
        Receptor pc = new Receptor();
        conectar(campo(tokens, "$.access_token"), pc);

        mvc.perform(delete("/api/pcs/" + campo(tokens, "$.pc_id")).header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5)).until(() -> pc.cierre.get() != null);
        assertThat(pc.textos).containsExactly("{\"desvincular\":true}");
        assertThat(pc.cierre.get().getCode()).isEqualTo(4001);
        // It cannot connect again.
        assertThatThrownBy(() -> conectar(campo(tokens, "$.access_token"), new Receptor()))
                .hasStackTraceContaining("401");
    }

    @Test
    void ca31_1_lasCamarasDeLaPcQuitadaTambienRecibenDesvincular() throws Exception {
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, sesion, "Sala");
        Receptor camara = new Receptor();
        conectar(campo(registro, "$.token"), camara);
        String pcId = campo(
                mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[0].id");

        mvc.perform(delete("/api/pcs/" + pcId).header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5)).until(() -> camara.cierre.get() != null);
        assertThat(camara.textos).contains("{\"desvincular\":true}");
    }
}
