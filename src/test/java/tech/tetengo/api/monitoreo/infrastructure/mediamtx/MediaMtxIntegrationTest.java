package tech.tetengo.api.monitoreo.infrastructure.mediamtx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tech.tetengo.api.monitoreo.application.port.ServicioDeTransmision;
import tech.tetengo.api.shared.infrastructure.security.Secretos;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.TestcontainersConfiguration;

/**
 * Live view end to end with a real MediaMTX, configured by the repository's {@code mediamtx.yml}: its
 * authorization hook calls this API, an ffmpeg container plays the household agent (publishing what the
 * control channel tells it), and the HLS playlist is read like the app does. The API runs on a fixed
 * free port that MediaMTX reaches through Testcontainers' host port forwarding.
 */
@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location=",
            "tetengo.tareas.habilitadas=false",
            "tetengo.vista-en-vivo.secreto-autorizacion=" + MediaMtxIntegrationTest.SECRETO
        })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JwtDePrueba.class})
class MediaMtxIntegrationTest {

    static final String SECRETO = "secreto-mediamtx-de-prueba";

    /** Same image as compose.yaml; the -ffmpeg variant only plays the agent. */
    static final DockerImageName MEDIAMTX = DockerImageName.parse("bluenviron/mediamtx:1.21.1");

    static final DockerImageName FFMPEG = DockerImageName.parse("bluenviron/mediamtx:1.21.1-ffmpeg");

    static final int PUERTO_API = puertoLibre();

    static final Network RED = Network.newNetwork();

    static final GenericContainer<?> mediamtx = mediamtx();

    @SuppressWarnings("resource")
    private static GenericContainer<?> mediamtx() {
        Testcontainers.exposeHostPorts(PUERTO_API);
        GenericContainer<?> contenedor = new GenericContainer<>(MEDIAMTX)
                .withCopyFileToContainer(MountableFile.forHostPath("mediamtx.yml"), "/mediamtx.yml")
                .withEnv(
                        "MTX_AUTHHTTPADDRESS",
                        "http://host.testcontainers.internal:%d/api/interno/mediamtx/autorizar?secreto=%s"
                                .formatted(PUERTO_API, SECRETO))
                .withNetwork(RED)
                .withNetworkAliases("mediamtx")
                .withExposedPorts(8888, 9997)
                .waitingFor(Wait.forLogMessage(".*\\[API\\] started.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
        contenedor.start();
        return contenedor;
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void vistaEnVivo(DynamicPropertyRegistry registro) {
        registro.add("server.port", () -> PUERTO_API);
        registro.add("tetengo.vista-en-vivo.url-publicacion", () -> "rtsp://mediamtx:8554/camaras/{camaraId}");
        registro.add(
                "tetengo.vista-en-vivo.url-hls",
                () -> "http://%s:%d".formatted(mediamtx.getHost(), mediamtx.getMappedPort(8888)));
        registro.add(
                "tetengo.vista-en-vivo.mediamtx-api",
                () -> "http://%s:%d".formatted(mediamtx.getHost(), mediamtx.getMappedPort(9997)));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ServicioDeTransmision servicio;

    final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    String token;
    String tokenAgente;
    String camara;
    Receptor delAgente;
    WebSocketSession canal;
    GenericContainer<?> publicador;

    static class Receptor extends TextWebSocketHandler {
        final List<String> textos = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
            textos.add(mensaje.getPayload());
        }
    }

    @BeforeEach
    void agenteConectado() throws Exception {
        String correo = "ana-" + UUID.randomUUID() + "@correo.pe";
        String titular = ApiDePrueba.titularConHogar(mvc, correo, "Ana");
        token = campo(titular, "$.tokenAcceso");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        tokenAgente = campo(registro, "$.token");
        camara = campo(registro, "$.camaraId");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(tokenAgente)))
                .andExpect(status().isOk());
        var cabeceras = new WebSocketHttpHeaders();
        cabeceras.add("Authorization", bearer(tokenAgente));
        delAgente = new Receptor();
        canal = new StandardWebSocketClient()
                .execute(delAgente, cabeceras, URI.create("ws://localhost:" + PUERTO_API + "/api/agente/transmision"))
                .get();
    }

    @AfterEach
    void detener() throws Exception {
        if (publicador != null) {
            publicador.stop();
        }
        if (canal.isOpen()) {
            canal.close();
        }
    }

    private String abrirVistaEnVivo() throws Exception {
        return mvc.perform(post("/api/camaras/" + camara + "/vista-en-vivo")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String ordenDeTransmitir() {
        await().atMost(Duration.ofSeconds(10)).until(() -> !delAgente.textos.isEmpty());
        String orden = delAgente.textos.getFirst();
        assertThat(campo(orden, "$.transmitir")).isEqualTo("true");
        return orden;
    }

    /** ffmpeg playing the agent: H.264 test pattern, 480p, 8 fps, GOP of 1 s, RTSP over TCP. */
    @SuppressWarnings("resource")
    private GenericContainer<?> ffmpeg(String urlConCredenciales) {
        return new GenericContainer<>(FFMPEG)
                .withNetwork(RED)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("ffmpeg"))
                .withCommand(
                        "-hide_banner",
                        "-loglevel",
                        "warning",
                        "-re",
                        "-f",
                        "lavfi",
                        "-i",
                        "testsrc=size=640x480:rate=8",
                        "-t",
                        "120",
                        "-c:v",
                        "libx264",
                        "-preset",
                        "ultrafast",
                        "-tune",
                        "zerolatency",
                        "-g",
                        "8",
                        "-pix_fmt",
                        "yuv420p",
                        "-f",
                        "rtsp",
                        "-rtsp_transport",
                        "tcp",
                        urlConCredenciales);
    }

    private static String conCredenciales(String orden, String clave) {
        URI url = URI.create(campo(orden, "$.urlPublicacion"));
        return "rtsp://%s:%s@%s:%d%s"
                .formatted(campo(orden, "$.usuario"), clave, url.getHost(), url.getPort(), url.getPath());
    }

    private void publicar(String orden) {
        publicador = ffmpeg(conCredenciales(orden, campo(orden, "$.clave")));
        publicador.start();
        await().atMost(Duration.ofSeconds(30)).until(this::enLinea);
    }

    private boolean enLinea() throws Exception {
        HttpResponse<String> ruta = http.send(
                HttpRequest.newBuilder(URI.create("http://%s:%d/v3/paths/get/camaras/%s"
                                .formatted(mediamtx.getHost(), mediamtx.getMappedPort(9997), camara)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return ruta.statusCode() == 200 && "true".equals(campo(ruta.body(), "$.online"));
    }

    private HttpResponse<String> obtener(String url) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String sinToken(String url) {
        return url.substring(0, url.indexOf('?'));
    }

    @Test
    void elAgentePublicaConLaClaveQueRecibioYConOtraSeRechaza() throws Exception {
        abrirVistaEnVivo();
        String orden = ordenDeTransmitir();

        GenericContainer<?> intruso = ffmpeg(conCredenciales(orden, "clave-inventada"))
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(60)));
        assertThatThrownBy(intruso::start).isInstanceOf(RuntimeException.class);
        intruso.stop();
        assertThat(enLinea()).isFalse();

        publicar(orden);
        assertThat(enLinea()).isTrue();
    }

    @Test
    void laAplicacionLeeElPlaylistHlsConSuTokenYSinTokenRecibe401() throws Exception {
        String sesion = abrirVistaEnVivo();
        publicar(ordenDeTransmitir());
        String url = campo(sesion, "$.urlTransmision");

        await().atMost(Duration.ofSeconds(30)).until(() -> obtener(url).statusCode() == 200);
        assertThat(obtener(url).body()).startsWith("#EXTM3U");
        assertThat(obtener(sinToken(url)).statusCode()).isEqualTo(401);
        assertThat(obtener(sinToken(url) + "?token=otro").statusCode()).isEqualTo(401);

        String huella = Secretos.huella(URI.create(url).getQuery().substring("token=".length()));
        assertThat(servicio.lectores()).anySatisfy(lector -> {
            assertThat(lector.camaraId()).hasToString(camara);
            assertThat(lector.huellaToken()).isEqualTo(huella);
        });
        assertThat(jdbc.queryForObject(
                        "select conectada_en is not null from accesos_vista_en_vivo where id = ?",
                        Boolean.class,
                        UUID.fromString(campo(sesion, "$.sesionId"))))
                .isTrue();

        mvc.perform(delete("/api/vista-en-vivo/" + campo(sesion, "$.sesionId")).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(obtener(url).statusCode()).isEqualTo(401);
    }

    @Test
    void alPausarLaCamaraElAgenteRecibeTransmitirFalseYMediaMtxExpulsaAlPublicadorYALosLectores() throws Exception {
        String sesion = abrirVistaEnVivo();
        publicar(ordenDeTransmitir());
        String url = campo(sesion, "$.urlTransmision");
        await().atMost(Duration.ofSeconds(30)).until(() -> obtener(url).statusCode() == 200);

        mvc.perform(post("/api/camaras/" + camara + "/pausa")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracion\":\"MIN_30\"}"))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).until(() -> delAgente.textos.contains("{\"transmitir\":false}"));
        await().atMost(Duration.ofSeconds(15)).until(() -> !enLinea());
        await().atMost(Duration.ofSeconds(15)).until(() -> !publicador.isRunning());
        assertThat(servicio.lectores())
                .noneSatisfy(lector -> assertThat(lector.camaraId()).hasToString(camara));
        assertThat(obtener(url).statusCode()).isEqualTo(401);
    }

    @Test
    void alRevocarseElConsentimientoElAgenteRecibeTransmitirFalseYMediaMtxLoExpulsa() throws Exception {
        abrirVistaEnVivo();
        publicar(ordenDeTransmitir());

        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(10)).until(() -> delAgente.textos.contains("{\"transmitir\":false}"));
        await().atMost(Duration.ofSeconds(15)).until(() -> !enLinea());
        await().atMost(Duration.ofSeconds(15)).until(() -> !publicador.isRunning());
    }
}
