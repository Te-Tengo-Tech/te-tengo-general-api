package tech.tetengo.api.shared.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.tetengo.api.shared.application.port.NotificadorCorreo.Correo;
import tech.tetengo.api.support.Floci;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The SES adapter against Floci: the message arrives with its sender, subject and UTF-8 body. */
@Tag("integration")
@Testcontainers
class NotificadorCorreoEnSesIntegrationTest {

    @Container
    static final GenericContainer<?> FLOCI = Floci.contenedor();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static NotificadorCorreoEnSes notificador;
    private static URI endpoint;

    @BeforeAll
    static void conectar() {
        endpoint = Floci.endpoint(FLOCI);
        notificador = NotificadorCorreoEnSes.crear(new PropiedadesDeSes(
                "Te Tengo <no-responder@tetengo.test>", "us-east-1", endpoint, Floci.CLAVE, Floci.CLAVE));
    }

    @AfterAll
    static void cerrar() {
        notificador.close();
    }

    @Test
    void elCorreoLlegaConRemitenteAsuntoYCuerpo() throws Exception {
        notificador.enviar(new Correo(
                "ana@correo.pe", "Te Tengo: restablece tu contraseña", "Hola, Ana:\n\ntetengo://app/x?token=abc"));

        JsonNode mensajes = bandeja().path("messages");
        assertThat(mensajes).hasSize(1);
        JsonNode mensaje = mensajes.get(0);
        assertThat(mensaje.path("Source").asString()).contains("no-responder@tetengo.test");
        assertThat(mensaje.path("Destination").path("ToAddresses").get(0).asString())
                .isEqualTo("ana@correo.pe");
        assertThat(mensaje.path("Subject").asString()).isEqualTo("Te Tengo: restablece tu contraseña");
        assertThat(mensaje.path("Body").path("text_part").asString()).contains("Hola, Ana:", "token=abc");
    }

    @Test
    void unFalloDeSesNoLlegaAlLlamador() {
        var sinServicio = NotificadorCorreoEnSes.crear(new PropiedadesDeSes(
                "no-responder@tetengo.test", "us-east-1", URI.create("http://127.0.0.1:9"), "x", "x"));
        assertThatCode(() -> sinServicio.enviar(new Correo("ana@correo.pe", "Asunto", "Cuerpo")))
                .doesNotThrowAnyException();
        sinServicio.close();
    }

    private static JsonNode bandeja() throws Exception {
        HttpResponse<String> respuesta = HTTP.send(
                HttpRequest.newBuilder(endpoint.resolve("/_aws/ses")).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return JsonMapper.builder().build().readTree(respuesta.body());
    }
}
