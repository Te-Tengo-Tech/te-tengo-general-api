package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips.Subida;

/**
 * The S3 adapter against SeaweedFS, the S3-compatible store of {@code compose.yaml}: the agent
 * uploads through the pre-signed PUT URL with the returned headers, and the app reads through the
 * pre-signed GET URL.
 */
@Tag("integration")
@Testcontainers
class AlmacenamientoDeClipsEnS3IntegrationTest {

    private static final int PUERTO_S3 = 8333;

    @Container
    static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>(
                    DockerImageName.parse("chrislusf/seaweedfs:4.48"))
            .withCommand("server", "-s3", "-dir=/data")
            .withExposedPorts(PUERTO_S3)
            .waitingFor(Wait.forHttp("/").forPort(PUERTO_S3).forStatusCodeMatching(estado -> estado < 500))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static AlmacenamientoDeClipsEnS3 almacenamiento;
    private static URI endpoint;

    @BeforeAll
    static void conectar() {
        endpoint = URI.create("http://%s:%d".formatted(SEAWEEDFS.getHost(), SEAWEEDFS.getMappedPort(PUERTO_S3)));
        almacenamiento = AlmacenamientoDeClipsEnS3.crear(
                new PropiedadesDeS3("te-tengo-clips", "us-east-1", endpoint, true, "local", "local", true),
                Clock.systemUTC());
    }

    @AfterAll
    static void cerrar() {
        almacenamiento.close();
    }

    private static Instant enDiezMinutos() {
        return Instant.now().plus(Duration.ofMinutes(10));
    }

    @Test
    void elAgenteSubeConLaUrlFirmadaYElClipExiste() throws Exception {
        String clave = "hogares/h1/alertas/a1/e1";
        Subida subida = almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos());

        assertThat(subida.url().toString())
                .startsWith(endpoint + "/te-tengo-clips/" + clave)
                .contains("X-Amz-Signature=");
        assertThat(subida.cabeceras()).containsEntry("Content-Type", "video/mp4");
        assertThat(almacenamiento.existe(clave)).isFalse();

        HttpRequest.Builder put =
                HttpRequest.newBuilder(subida.url()).PUT(HttpRequest.BodyPublishers.ofString("mp4 de prueba"));
        subida.cabeceras().forEach(put::header);
        assertThat(HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);

        assertThat(almacenamiento.existe(clave)).isTrue();
    }

    @Test
    void laAppLeeYDescargaElClipConUrlsFirmadas() throws Exception {
        String clave = "hogares/h1/alertas/a2/e2";
        Subida subida = almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos());
        HttpRequest.Builder put = HttpRequest.newBuilder(subida.url()).PUT(HttpRequest.BodyPublishers.ofString("clip"));
        subida.cabeceras().forEach(put::header);
        HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> lectura = HTTP.send(
                HttpRequest.newBuilder(almacenamiento.urlDeLectura(clave, enDiezMinutos(), false, "clip"))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(lectura.statusCode()).isEqualTo(200);
        assertThat(lectura.body()).isEqualTo("clip");

        URI descarga = almacenamiento.urlDeLectura(clave, enDiezMinutos(), true, "te-tengo-caida");
        assertThat(descarga.getRawQuery()).contains("response-content-disposition=attachment");
        assertThat(HTTP.send(HttpRequest.newBuilder(descarga).build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);
    }

    @Test
    void eliminarBorraElClip() throws Exception {
        String clave = "hogares/h1/alertas/a3/e3";
        Subida subida = almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos());
        HttpRequest.Builder put = HttpRequest.newBuilder(subida.url()).PUT(HttpRequest.BodyPublishers.ofString("clip"));
        subida.cabeceras().forEach(put::header);
        HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding());
        assertThat(almacenamiento.existe(clave)).isTrue();

        almacenamiento.eliminar(clave);

        assertThat(almacenamiento.existe(clave)).isFalse();
        almacenamiento.eliminar(clave);
    }
}
