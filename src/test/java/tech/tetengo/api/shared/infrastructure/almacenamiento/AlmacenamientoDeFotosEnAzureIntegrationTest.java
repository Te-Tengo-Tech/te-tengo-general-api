package tech.tetengo.api.shared.infrastructure.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Subida;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.CuentaDeAzureBlob;
import tech.tetengo.api.support.Azurite;

/** The photos' Azure Blob adapter against Azurite: SAS upload, then the backend reads, writes and deletes. */
@Tag("integration")
@Testcontainers
class AlmacenamientoDeFotosEnAzureIntegrationTest {

    @Container
    static final GenericContainer<?> AZURITE = Azurite.contenedor();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static AlmacenamientoDeFotosEnAzure almacenamiento;

    @BeforeAll
    static void conectar() {
        almacenamiento = new AlmacenamientoDeFotosEnAzure(
                CuentaDeAzureBlob.crear(Azurite.propiedades(AZURITE), Clock.systemUTC())
                        .contenedor("clips"));
    }

    @Test
    void laAppSubeLaFotoYElBackendLaLeeGuardaYElimina() throws Exception {
        String subidaClave = "fotos/subidas/s1";
        Subida subida = almacenamiento.urlDeSubida(
                subidaClave, "image/png", 4, Instant.now().plusSeconds(600));
        assertThat(subida.cabeceras()).containsEntry("x-ms-blob-type", "BlockBlob");
        HttpRequest.Builder put = HttpRequest.newBuilder(subida.url()).PUT(HttpRequest.BodyPublishers.ofString("png!"));
        subida.cabeceras().forEach(put::header);
        assertThat(HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(201);

        assertThat(almacenamiento.tamano(subidaClave)).hasValue(4L);
        assertThat(almacenamiento.leer(subidaClave))
                .hasValueSatisfying(b -> assertThat(new String(b)).isEqualTo("png!"));

        String clave = "fotos/usuarios/u1.jpg";
        almacenamiento.guardar(clave, new byte[] {1, 2, 3}, "image/jpeg");
        HttpResponse<byte[]> lectura = HTTP.send(
                HttpRequest.newBuilder(
                                almacenamiento.urlDeLectura(clave, Instant.now().plusSeconds(3600)))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(lectura.statusCode()).isEqualTo(200);
        assertThat(lectura.body()).containsExactly(1, 2, 3);
        assertThat(lectura.headers().firstValue("Content-Type")).hasValue("image/jpeg");

        almacenamiento.eliminar(clave);
        almacenamiento.eliminar(clave);
        assertThat(almacenamiento.leer(clave)).isEmpty();
        assertThat(almacenamiento.tamano(clave)).isEmpty();
    }
}
