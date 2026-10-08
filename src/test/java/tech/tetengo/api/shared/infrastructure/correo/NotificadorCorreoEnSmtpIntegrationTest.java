package tech.tetengo.api.shared.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.tetengo.api.shared.application.port.NotificadorCorreo.Correo;
import tech.tetengo.api.support.Mailpit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SMTP adapter against Mailpit: the message arrives with its sender, subject and UTF-8 body,
 * the same content as the SES adapter. Mailpit has no TLS here, so STARTTLS stays off.
 */
@Tag("integration")
@Testcontainers
class NotificadorCorreoEnSmtpIntegrationTest {

    @Container
    static final GenericContainer<?> MAILPIT = Mailpit.contenedor();

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void elCorreoLlegaConRemitenteAsuntoYCuerpo() throws Exception {
        var notificador = new NotificadorCorreoEnSmtp(
                enviador(MAILPIT.getHost(), MAILPIT.getMappedPort(Mailpit.PUERTO_SMTP)),
                "Te Tengo <no-responder@tetengo.test>");

        notificador.enviar(new Correo(
                "ana@correo.pe", "Te Tengo: restablece tu contraseña", "Hola, Ana:\n\ntetengo://app/x?token=abc"));

        JsonNode mensajes = leer("/api/v1/messages").path("messages");
        assertThat(mensajes).hasSize(1);
        JsonNode resumen = mensajes.get(0);
        assertThat(resumen.path("From").path("Address").asString()).isEqualTo("no-responder@tetengo.test");
        assertThat(resumen.path("From").path("Name").asString()).isEqualTo("Te Tengo");
        assertThat(resumen.path("To").get(0).path("Address").asString()).isEqualTo("ana@correo.pe");
        assertThat(resumen.path("Subject").asString()).isEqualTo("Te Tengo: restablece tu contraseña");

        JsonNode mensaje = leer("/api/v1/message/" + resumen.path("ID").asString());
        assertThat(mensaje.path("Text").asString()).contains("Hola, Ana:", "tetengo://app/x?token=abc");
        assertThat(mensaje.path("HTML").asString()).isEmpty();
    }

    @Test
    void unFalloDelServidorNoLlegaAlLlamador() {
        var sinServidor = new NotificadorCorreoEnSmtp(enviador("127.0.0.1", 9), "no-responder@tetengo.test");
        assertThatCode(() -> sinServidor.enviar(new Correo("ana@correo.pe", "Asunto", "Cuerpo")))
                .doesNotThrowAnyException();
    }

    private static JavaMailSenderImpl enviador(String host, int puerto) {
        var enviador = new JavaMailSenderImpl();
        enviador.setHost(host);
        enviador.setPort(puerto);
        enviador.setDefaultEncoding(StandardCharsets.UTF_8.name());
        enviador.getJavaMailProperties().put("mail.smtp.connectiontimeout", "5000");
        enviador.getJavaMailProperties().put("mail.smtp.timeout", "5000");
        return enviador;
    }

    private static JsonNode leer(String ruta) throws Exception {
        HttpResponse<String> respuesta = HTTP.send(
                HttpRequest.newBuilder(Mailpit.api(MAILPIT).resolve(URI.create(ruta)))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return JSON.readTree(respuesta.body());
    }
}
