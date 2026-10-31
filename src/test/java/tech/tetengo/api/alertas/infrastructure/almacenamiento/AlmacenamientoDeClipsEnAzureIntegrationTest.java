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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips.Subida;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.CuentaDeAzureBlob;
import tech.tetengo.api.support.Azurite;

/**
 * The Azure Blob adapter of the clips against Azurite, which verifies SAS signatures: the agent
 * uploads through the SAS PUT URL with exactly the returned headers, and the app reads and downloads
 * through SAS GET URLs. A tampered URL, or a PUT without {@code x-ms-blob-type}, is refused.
 */
@Tag("integration")
@Testcontainers
class AlmacenamientoDeClipsEnAzureIntegrationTest {

    @Container
    static final GenericContainer<?> AZURITE = Azurite.contenedor();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static AlmacenamientoDeClipsEnAzure almacenamiento;

    @BeforeAll
    static void conectar() {
        almacenamiento = new AlmacenamientoDeClipsEnAzure(
                CuentaDeAzureBlob.crear(Azurite.propiedades(AZURITE), Clock.systemUTC())
                        .contenedor("clips"));
    }

    private static Instant enDiezMinutos() {
        return Instant.now().plus(Duration.ofMinutes(10));
    }

    private static int subir(Subida subida, String cuerpo) throws Exception {
        HttpRequest.Builder put = HttpRequest.newBuilder(subida.url()).PUT(HttpRequest.BodyPublishers.ofString(cuerpo));
        subida.cabeceras().forEach(put::header);
        return HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void elAgenteSubeConLaUrlSasYLasCabecerasDevueltasYElClipExiste() throws Exception {
        String clave = "hogares/h1/alertas/a1/e1";
        Subida subida = almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos());

        assertThat(subida.url().toString())
                .startsWith(Azurite.endpoint(AZURITE) + "/clips/" + clave + "?")
                .contains("sig=", "sp=cw", "spr=https%2Chttp");
        assertThat(subida.cabeceras())
                .containsEntry("Content-Type", "video/mp4")
                .containsEntry("x-ms-blob-type", "BlockBlob")
                .hasSize(2);
        assertThat(almacenamiento.existe(clave)).isFalse();

        assertThat(subir(subida, "mp4 de prueba")).isEqualTo(201);

        assertThat(almacenamiento.existe(clave)).isTrue();
    }

    @Test
    void laAppLeeYDescargaElClipConUrlsSas() throws Exception {
        String clave = "hogares/h1/alertas/a2/e2";
        subir(almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos()), "clip");

        HttpResponse<String> lectura = HTTP.send(
                HttpRequest.newBuilder(almacenamiento.urlDeLectura(clave, enDiezMinutos(), false, "clip"))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(lectura.statusCode()).isEqualTo(200);
        assertThat(lectura.body()).isEqualTo("clip");
        assertThat(lectura.headers().firstValue("Content-Type")).hasValue("video/mp4");

        URI descarga = almacenamiento.urlDeLectura(clave, enDiezMinutos(), true, "te-tengo-caida");
        HttpResponse<Void> respuesta =
                HTTP.send(HttpRequest.newBuilder(descarga).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"te-tengo-caida.mp4\"");
    }

    @Test
    void unaUrlSasAlteradaSeRechaza() throws Exception {
        String clave = "hogares/h1/alertas/a4/e4";
        Subida subida = almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos());
        Subida alterada = new Subida(
                URI.create(subida.url().toString().replace(clave, "hogares/h2/alertas/a4/e4")), subida.cabeceras());

        assertThat(subir(alterada, "clip")).isEqualTo(403);
    }

    @Test
    void laUrlDeLecturaNoSirveParaSubir() throws Exception {
        String clave = "hogares/h1/alertas/a5/e5";
        Subida lecturaComoSubida = new Subida(
                almacenamiento.urlDeLectura(clave, enDiezMinutos(), false, "clip"),
                almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos()).cabeceras());

        assertThat(subir(lecturaComoSubida, "clip")).isEqualTo(403);
        assertThat(almacenamiento.existe(clave)).isFalse();
    }

    @Test
    void eliminarBorraElClipYNoFallaSiYaNoEsta() throws Exception {
        String clave = "hogares/h1/alertas/a3/e3";
        subir(almacenamiento.urlDeSubida(clave, "video/mp4", enDiezMinutos()), "clip");
        assertThat(almacenamiento.existe(clave)).isTrue();

        almacenamiento.eliminar(clave);

        assertThat(almacenamiento.existe(clave)).isFalse();
        almacenamiento.eliminar(clave);
    }
}
