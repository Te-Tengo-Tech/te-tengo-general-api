package tech.tetengo.api.monitoreo.infrastructure.transmision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;

/**
 * US-23 over a real WebSocket: the agent's control channel (AGENT_CONTRACT.md) follows the camera's
 * live view sessions, mode, pause and consent.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location=",
            "tetengo.tareas.habilitadas=false",
            AbstractIntegrationTest.SECRETO_MEDIAMTX
        })
class TransmisionEnVivoIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int puerto;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String token;
    String agente;
    String camara;
    WebSocketSession canal;
    Receptor delAgente;

    static class Receptor extends TextWebSocketHandler {
        final List<String> textos = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
            textos.add(mensaje.getPayload());
        }

        String ultimo() {
            return textos.getLast();
        }
    }

    @BeforeEach
    void agenteConectado() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        agente = campo(registro, "$.token");
        camara = campo(registro, "$.camaraId");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(agente)))
                .andExpect(status().isOk());
        delAgente = new Receptor();
        canal = conectarAgente(delAgente);
    }

    @AfterEach
    void cerrarCanal() throws Exception {
        if (canal != null && canal.isOpen()) {
            canal.close();
        }
    }

    private WebSocketSession conectarAgente(Receptor receptor) throws Exception {
        var cabeceras = new WebSocketHttpHeaders();
        cabeceras.add("Authorization", bearer(agente));
        return new StandardWebSocketClient()
                .execute(receptor, cabeceras, URI.create("ws://localhost:" + puerto + "/api/agente/transmision"))
                .get();
    }

    private String abrir(String cuerpo) throws Exception {
        return mvc.perform(post("/api/camaras/" + camara + "/vista-en-vivo")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void cerrar(String sesion) throws Exception {
        mvc.perform(delete("/api/vista-en-vivo/" + campo(sesion, "$.sesionId")).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    private ResultActions publicar(String clave) throws Exception {
        return mvc.perform(post("/api/interno/mediamtx/autorizar?secreto=secreto-de-prueba")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"user\":\"agente\",\"password\":\"%s\",\"action\":\"publish\",\"path\":\"camaras/%s\",\"query\":\"\"}"
                                .formatted(clave, camara)));
    }

    private void esperarMensajes(Receptor receptor, int cantidad) {
        await().atMost(Duration.ofSeconds(5)).until(() -> receptor.textos.size() >= cantidad);
    }

    @Test
    void laPrimeraSesionPideTransmitirConLaUrlLaClaveYElModo() throws Exception {
        abrir("{}");
        esperarMensajes(delAgente, 1);
        String mensaje = delAgente.ultimo();
        assertThat(campo(mensaje, "$.transmitir")).isEqualTo("true");
        assertThat(campo(mensaje, "$.urlPublicacion")).isEqualTo("rtsp://localhost:8554/camaras/" + camara);
        assertThat(campo(mensaje, "$.usuario")).isEqualTo("agente");
        assertThat(campo(mensaje, "$.modo")).isEqualTo("VIDEO");
        assertThat(mensaje).startsWith("{\"transmitir\":true,\"urlPublicacion\":");
        publicar(campo(mensaje, "$.clave")).andExpect(status().isOk());
    }

    @Test
    void elModoCambiaDuranteLaTransmisionYLaUltimaSesionLaDetiene() throws Exception {
        String primera = abrir("{\"modo\":\"VIDEO_CON_POSTURA\"}");
        esperarMensajes(delAgente, 1);
        assertThat(campo(delAgente.ultimo(), "$.modo")).isEqualTo("VIDEO_CON_POSTURA");
        String segunda = abrir("{}");

        mvc.perform(patch("/api/vista-en-vivo/" + campo(segunda, "$.sesionId"))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modo\":\"SOLO_POSTURA\"}"))
                .andExpect(status().isOk());
        esperarMensajes(delAgente, 2);
        assertThat(delAgente.ultimo()).isEqualTo("{\"modo\":\"SOLO_POSTURA\"}");

        cerrar(primera);
        cerrar(segunda);
        esperarMensajes(delAgente, 3);
        assertThat(delAgente.textos)
                .containsExactly(delAgente.textos.getFirst(), "{\"modo\":\"SOLO_POSTURA\"}", "{\"transmitir\":false}");
    }

    @Test
    void alReconectarseConSesionesAbiertasElAgenteRecibeUnaClaveNueva() throws Exception {
        abrir("{}");
        esperarMensajes(delAgente, 1);
        String claveAnterior = campo(delAgente.ultimo(), "$.clave");

        Receptor reconectado = new Receptor();
        WebSocketSession nuevo = conectarAgente(reconectado);
        try {
            esperarMensajes(reconectado, 1);
            String clave = campo(reconectado.ultimo(), "$.clave");
            assertThat(campo(reconectado.ultimo(), "$.transmitir")).isEqualTo("true");
            assertThat(clave).isNotEqualTo(claveAnterior);
            publicar(clave).andExpect(status().isOk());
            publicar(claveAnterior).andExpect(status().isUnauthorized());
            // The new connection replaces the previous one.
            await().atMost(Duration.ofSeconds(5)).until(() -> !canal.isOpen());
        } finally {
            nuevo.close();
        }
    }

    private void preparar() throws Exception {
        mvc.perform(post("/api/vista-en-vivo/preparar")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"camaraId\":\"%s\"}".formatted(camara)))
                .andExpect(status().isNoContent());
    }

    @Test
    void alAbrirLaPantallaDeLaCamaraElAgenteRecibePrepararSinCredenciales() throws Exception {
        preparar();
        esperarMensajes(delAgente, 1);
        assertThat(delAgente.textos).containsExactly("{\"preparar\":true}");

        // The live view that follows starts as always.
        abrir("{}");
        esperarMensajes(delAgente, 2);
        assertThat(campo(delAgente.ultimo(), "$.transmitir")).isEqualTo("true");
    }

    @Test
    void mientrasLaCamaraTransmiteNoSeEnviaPreparar() throws Exception {
        abrir("{}");
        esperarMensajes(delAgente, 1);
        preparar();
        Thread.sleep(300);
        assertThat(delAgente.textos).hasSize(1);
    }

    @Test
    void sinSesionesAbiertasElAgenteQueSeConectaNoRecibeNada() throws Exception {
        Thread.sleep(300);
        assertThat(delAgente.textos).isEmpty();
    }

    @Test
    void ca22_1_alPausarLaCamaraElAgenteDejaDeTransmitir() throws Exception {
        abrir("{}");
        esperarMensajes(delAgente, 1);
        mvc.perform(post("/api/camaras/" + camara + "/pausa")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracion\":\"HORA_1\"}"))
                .andExpect(status().isOk());
        esperarMensajes(delAgente, 2);
        assertThat(delAgente.ultimo()).isEqualTo("{\"transmitir\":false}");
        assertThat(transmision.camarasExpulsadas()).contains(UUID.fromString(camara));
    }

    @Test
    void ca09_1_alRevocarseElConsentimientoElAgenteDejaDeTransmitir() throws Exception {
        abrir("{}");
        esperarMensajes(delAgente, 1);
        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isAccepted());
        esperarMensajes(delAgente, 2);
        assertThat(delAgente.ultimo()).isEqualTo("{\"transmitir\":false}");
        await().atMost(Duration.ofSeconds(5))
                .until(() -> transmision.camarasExpulsadas().contains(UUID.fromString(camara)));
    }

    @Test
    void sinElTokenDelAgenteNoHayCanal() {
        var cliente = new StandardWebSocketClient();
        URI url = URI.create("ws://localhost:" + puerto + "/api/agente/transmision");
        boolean rechazado;
        try {
            cliente.execute(new Receptor(), new WebSocketHttpHeaders(), url).get();
            rechazado = false;
        } catch (Exception e) {
            rechazado = true;
        }
        assertThat(rechazado).isTrue();
        var familiar = new WebSocketHttpHeaders();
        familiar.add("Authorization", bearer(token));
        try {
            cliente.execute(new Receptor(), familiar, url).get();
            rechazado = false;
        } catch (Exception e) {
            rechazado = true;
        }
        assertThat(rechazado).isTrue();
    }
}
