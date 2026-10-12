package tech.tetengo.api.shared.infrastructure.almacenamiento;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Subida;
import tech.tetengo.api.support.Floci;

/**
 * The photo store against Floci with signature verification on: the app uploads through the pre-signed
 * PUT with the returned headers, the backend reads, writes and deletes, and the app reads the stored
 * photo through a pre-signed GET.
 */
@Tag("integration")
@Testcontainers
class AlmacenamientoDeFotosEnS3IntegrationTest {

    @Container
    static final GenericContainer<?> FLOCI = Floci.contenedor().withEnv("FLOCI_AUTH_VALIDATE_SIGNATURES", "true");

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static AlmacenamientoDeFotosEnS3 almacenamiento;
    private static URI endpoint;

    @BeforeAll
    static void conectar() {
        endpoint = Floci.endpoint(FLOCI);
        almacenamiento = AlmacenamientoDeFotosEnS3.crear(
                new PropiedadesDeAlmacenamientoDeFotos(
                        "te-tengo-clips", "us-east-1", endpoint, true, Floci.CLAVE, Floci.CLAVE, true),
                Clock.systemUTC());
    }

    @AfterAll
    static void cerrar() {
        almacenamiento.close();
    }

    @Test
    void laAppSubeConLaUrlFirmadaYElBackendLeeGuardaYBorra() throws Exception {
        byte[] contenido = "jpeg de prueba".getBytes(StandardCharsets.UTF_8);
        String subida = "fotos/subidas/s1";
        Subida firmada = almacenamiento.urlDeSubida(
                subida, "image/jpeg", contenido.length, Instant.now().plus(Duration.ofMinutes(10)));

        assertThat(firmada.url().toString()).startsWith(endpoint + "/te-tengo-clips/" + subida);
        assertThat(firmada.cabeceras()).containsOnlyKeys("Content-Type").containsEntry("Content-Type", "image/jpeg");
        assertThat(almacenamiento.tamano(subida)).isEmpty();
        assertThat(almacenamiento.leer(subida)).isEmpty();

        HttpRequest.Builder put =
                HttpRequest.newBuilder(firmada.url()).PUT(HttpRequest.BodyPublishers.ofByteArray(contenido));
        firmada.cabeceras().forEach(put::header);
        assertThat(HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);

        assertThat(almacenamiento.tamano(subida)).contains((long) contenido.length);
        assertThat(almacenamiento.leer(subida)).hasValue(contenido);

        String clave = "fotos/usuarios/u1/f1.jpg";
        almacenamiento.guardar(clave, contenido, "image/jpeg");
        Foto foto = almacenamiento.foto(clave, Instant.now());
        HttpResponse<byte[]> leida =
                HTTP.send(HttpRequest.newBuilder(foto.url()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(leida.statusCode()).isEqualTo(200);
        assertThat(leida.body()).isEqualTo(contenido);
        assertThat(leida.headers().firstValue("Content-Type")).hasValue("image/jpeg");

        almacenamiento.eliminar(subida);
        almacenamiento.eliminar(subida);
        assertThat(almacenamiento.tamano(subida)).isEmpty();
    }

    @Test
    void unPutConOtroTipoSeRechaza() throws Exception {
        Subida firmada = almacenamiento.urlDeSubida(
                "fotos/subidas/s2", "image/png", 3, Instant.now().plus(Duration.ofMinutes(10)));

        HttpRequest put = HttpRequest.newBuilder(firmada.url())
                .header("Content-Type", "text/html")
                .PUT(HttpRequest.BodyPublishers.ofString("abc"))
                .build();
        assertThat(HTTP.send(put, HttpResponse.BodyHandlers.discarding()).statusCode())
                .isEqualTo(403);
    }
}
