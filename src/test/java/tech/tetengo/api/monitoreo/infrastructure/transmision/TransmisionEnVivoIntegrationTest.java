package tech.tetengo.api.monitoreo.infrastructure.transmision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;

/** US-23 end to end over real WebSockets: the agent's frames reach the app (contract proposal). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location=",
            "tetengo.tareas.habilitadas=false"
        })
class TransmisionEnVivoIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int puerto;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    static class Receptor extends AbstractWebSocketHandler {
        final List<String> textos = new CopyOnWriteArrayList<>();
        final List<byte[]> cuadros = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
            textos.add(mensaje.getPayload());
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession sesion, BinaryMessage mensaje) {
            ByteBuffer datos = mensaje.getPayload();
            byte[] copia = new byte[datos.remaining()];
            datos.get(copia);
            cuadros.add(copia);
        }
    }

    @Test
    void losCuadrosDelAgenteLleganALaAplicacionYAlCerrarSeRegistraElFin() throws Exception {
        String titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        String agente = campo(registro, "$.token");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(agente)))
                .andExpect(status().isNoContent());

        var cliente = new StandardWebSocketClient();
        var cabeceras = new WebSocketHttpHeaders();
        cabeceras.add("Authorization", bearer(agente));
        Receptor delAgente = new Receptor();
        WebSocketSession sesionDelAgente = cliente.execute(
                        delAgente, cabeceras, URI.create("ws://localhost:" + puerto + "/api/agente/transmision"))
                .get();

        String sesion = mvc.perform(post("/api/camaras/" + campo(registro, "$.camaraId") + "/vista-en-vivo")
                        .header("Authorization", bearer(campo(titular, "$.tokenAcceso")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        URI url = URI.create(campo(sesion, "$.urlTransmision"));
        URI local = URI.create("ws://localhost:" + puerto + url.getRawPath() + "?" + url.getRawQuery());

        Receptor app = new Receptor();
        WebSocketSession sesionDeLaApp =
                cliente.execute(app, new WebSocketHttpHeaders(), local).get();

        await().atMost(Duration.ofSeconds(5)).until(() -> delAgente.textos.contains("{\"transmitir\":true}"));
        byte[] cuadro = {
            0, 0, 1, (byte) 0x9A, 0x2B, 0x10, 0x00, 0x01, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9
        };
        sesionDelAgente.sendMessage(new BinaryMessage(cuadro));
        await().atMost(Duration.ofSeconds(5)).until(() -> !app.cuadros.isEmpty());
        assertThat(app.cuadros.getFirst()).isEqualTo(cuadro);

        // The URL works once.
        Receptor intruso = new Receptor();
        boolean rechazado;
        try {
            cliente.execute(intruso, new WebSocketHttpHeaders(), local).get();
            rechazado = false;
        } catch (Exception e) {
            rechazado = true;
        }
        assertThat(rechazado).isTrue();

        sesionDeLaApp.close();
        await().atMost(Duration.ofSeconds(5)).until(() -> delAgente.textos.contains("{\"transmitir\":false}"));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject(
                        "select fin is not null from accesos_vista_en_vivo where id = ?",
                        Boolean.class,
                        UUID.fromString(campo(sesion, "$.sesionId"))));
        sesionDelAgente.close();
    }

    @Test
    void sinUnTokenValidoNoHayTransmision() {
        var cliente = new StandardWebSocketClient();
        URI url = URI.create(
                "ws://localhost:" + puerto + "/api/vista-en-vivo/" + UUID.randomUUID() + "/transmision?token=x");
        boolean rechazado;
        try {
            cliente.execute(new Receptor(), new WebSocketHttpHeaders(), url).get();
            rechazado = false;
        } catch (Exception e) {
            rechazado = true;
        }
        assertThat(rechazado).isTrue();
    }
}
