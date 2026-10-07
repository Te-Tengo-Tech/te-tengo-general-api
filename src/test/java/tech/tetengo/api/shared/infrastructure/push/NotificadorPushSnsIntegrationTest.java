package tech.tetengo.api.shared.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sns.SnsClient;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Destino;
import tech.tetengo.api.shared.application.port.NotificadorPush.FallaDePush;
import tech.tetengo.api.shared.application.port.NotificadorPush.Plataforma;
import tech.tetengo.api.shared.application.port.NotificadorPush.Resultado;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.aws.ClienteAws;
import tech.tetengo.api.support.Floci;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SNS adapter against Floci, which captures mobile pushes instead of sending them: endpoints are
 * created once and reused, every platform gets its payload, and disabled endpoints are reported.
 */
@Tag("integration")
@Testcontainers
class NotificadorPushSnsIntegrationTest {

    @Container
    static final GenericContainer<?> FLOCI = Floci.contenedor();

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final UUID ALERTA = UUID.randomUUID();
    private static final Aviso CAIDA =
            new Aviso(TipoAviso.ALERTA_CAIDA, ALERTA, UUID.randomUUID(), "Sala", Instant.parse("2026-10-07T15:42:31Z"));

    private static URI endpoint;
    private static SnsClient sns;
    private static NotificadorPushSns notificador;

    @BeforeAll
    static void conectar() {
        endpoint = Floci.endpoint(FLOCI);
        sns = new ClienteAws("us-east-1", endpoint, Floci.CLAVE, Floci.CLAVE)
                .configurar(SnsClient.builder())
                .build();
        String android = sns.createPlatformApplication(
                        b -> b.name("android").platform("GCM").attributes(Map.of("PlatformCredential", "clave-fcm")))
                .platformApplicationArn();
        String ios = sns.createPlatformApplication(b ->
                        b.name("ios").platform("APNS").attributes(Map.of("PlatformCredential", "certificado-apns")))
                .platformApplicationArn();
        notificador = NotificadorPushSns.crear(
                new PropiedadesDeSns(android, ios, "us-east-1", endpoint, Floci.CLAVE, Floci.CLAVE, false));
    }

    @AfterAll
    static void cerrar() {
        notificador.close();
        sns.close();
    }

    @BeforeEach
    void vaciarCapturas() throws Exception {
        HTTP.send(
                HttpRequest.newBuilder(endpoint.resolve("/_aws/sns/push-notifications"))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static JsonNode capturas() throws Exception {
        return JSON.readTree(HTTP.send(
                                HttpRequest.newBuilder(endpoint.resolve("/_aws/sns/push-notifications"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                        .body())
                .path("notifications");
    }

    private static JsonNode capturaDe(String token) throws Exception {
        for (JsonNode captura : capturas()) {
            if (captura.path("Token").asString().equals(token)) {
                return captura;
            }
        }
        throw new AssertionError("Sin push capturado para " + token);
    }

    @Test
    void creaUnEndpointPorDispositivoYPublicaLaCargaDeCadaPlataforma() throws Exception {
        Resultado resultado = notificador.enviar(
                List.of(new Destino("fcm-ana", Plataforma.ANDROID), new Destino("apns-beto", Plataforma.IOS)), CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(2);
        assertThat(resultado.tokensInvalidos()).isEmpty();
        assertThat(resultado.referencias()).containsOnlyKeys("fcm-ana", "apns-beto");
        assertThat(resultado.referencias().get("fcm-ana")).contains(":endpoint/GCM/android/");
        assertThat(resultado.referencias().get("apns-beto")).contains(":endpoint/APNS/ios/");

        JsonNode android = capturaDe("fcm-ana");
        assertThat(android.path("Platform").asString()).isEqualTo("GCM");
        JsonNode mensaje = JSON.readTree(android.path("Payload").asString())
                .path("fcmV1Message")
                .path("message");
        assertThat(mensaje.path("notification").path("title").asString()).isEqualTo("Posible caída en la Sala");
        assertThat(mensaje.path("data").path("tipo").asString()).isEqualTo("ALERTA_CAIDA");
        assertThat(mensaje.path("data").path("alertaId").asString()).isEqualTo(ALERTA.toString());
        assertThat(mensaje.path("android").path("priority").asString()).isEqualTo("high");

        JsonNode ios = JSON.readTree(capturaDe("apns-beto").path("Payload").asString());
        assertThat(ios.path("aps").path("alert").path("body").asString()).isEqualTo("10:42 · Toca para ver qué hacer.");
        assertThat(ios.path("aps").path("sound").asString()).isEqualTo("default");
        assertThat(ios.path("gcm.message_id").asString()).isNotBlank();
        assertThat(ios.path("tipo").asString()).isEqualTo("ALERTA_CAIDA");
        assertThat(ios.path("habitacion").asString()).isEqualTo("Sala");
    }

    @Test
    void reusaElEndpointGuardado() throws Exception {
        String arn = notificador
                .enviar(List.of(new Destino("fcm-carla", Plataforma.ANDROID)), CAIDA)
                .referencias()
                .get("fcm-carla");

        Resultado resultado = notificador.enviar(List.of(new Destino("fcm-carla", Plataforma.ANDROID, arn)), CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(1);
        assertThat(resultado.referencias()).isEmpty();
        assertThat(capturas())
                .hasSize(2)
                .allSatisfy(c -> assertThat(c.path("EndpointArn").asString()).isEqualTo(arn));
    }

    @Test
    void unEndpointDeshabilitadoSeReportaComoTokenInvalidoYSeReactivaAlRegistrarseOtraVez() throws Exception {
        String arn = notificador
                .enviar(List.of(new Destino("fcm-dora", Plataforma.ANDROID)), CAIDA)
                .referencias()
                .get("fcm-dora");
        sns.setEndpointAttributes(b -> b.endpointArn(arn).attributes(Map.of("Enabled", "false")));

        Resultado deshabilitado = notificador.enviar(
                List.of(new Destino("fcm-dora", Plataforma.ANDROID, arn), new Destino("fcm-eva", Plataforma.ANDROID)),
                CAIDA);
        assertThat(deshabilitado.aceptados()).isEqualTo(1);
        assertThat(deshabilitado.tokensInvalidos()).containsExactly("fcm-dora");

        // The phone registered again: the device has no address, so the endpoint is re-enabled.
        Resultado reactivado = notificador.enviar(List.of(new Destino("fcm-dora", Plataforma.ANDROID)), CAIDA);
        assertThat(reactivado.aceptados()).isEqualTo(1);
        assertThat(reactivado.referencias()).containsEntry("fcm-dora", arn);
        assertThat(sns.getEndpointAttributes(b -> b.endpointArn(arn)).attributes())
                .containsEntry("Enabled", "true");
    }

    @Test
    void unEndpointBorradoSeCreaDeNuevo() {
        String arn = notificador
                .enviar(List.of(new Destino("fcm-fito", Plataforma.ANDROID)), CAIDA)
                .referencias()
                .get("fcm-fito");
        sns.deleteEndpoint(b -> b.endpointArn(arn));

        Resultado resultado = notificador.enviar(List.of(new Destino("fcm-fito", Plataforma.ANDROID, arn)), CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(1);
        assertThat(resultado.referencias()).containsKey("fcm-fito");
    }

    @Test
    void lasAplicacionesSeCreanSoloSiSePide() {
        try (var local = NotificadorPushSns.crear(
                new PropiedadesDeSns(null, null, "us-east-1", endpoint, Floci.CLAVE, Floci.CLAVE, true))) {
            assertThat(local.enviar(List.of(new Destino("fcm-gina", Plataforma.IOS)), CAIDA)
                            .aceptados())
                    .isEqualTo(1);
        }
        assertThatThrownBy(() -> NotificadorPushSns.crear(
                        new PropiedadesDeSns(null, null, "us-east-1", endpoint, Floci.CLAVE, Floci.CLAVE, false)))
                .hasMessageContaining("TT_SNS_ARN_ANDROID");
    }

    @Test
    void ca16_4_siSnsNoRespondeSeReintenta() {
        try (var sinServicio = NotificadorPushSns.crear(new PropiedadesDeSns(
                "arn:aws:sns:us-east-1:000000000000:app/GCM/android",
                "arn:aws:sns:us-east-1:000000000000:app/GCM/ios",
                "us-east-1",
                URI.create("http://127.0.0.1:9"),
                "x",
                "x",
                false))) {
            assertThatThrownBy(() -> sinServicio.enviar(List.of(new Destino("fcm-hugo", Plataforma.ANDROID)), CAIDA))
                    .isInstanceOf(FallaDePush.class);
        }
    }
}
