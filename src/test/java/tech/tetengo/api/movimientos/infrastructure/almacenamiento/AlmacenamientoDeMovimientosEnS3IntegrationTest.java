package tech.tetengo.api.movimientos.infrastructure.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.tetengo.api.support.Floci;

/**
 * The movement storage adapter against Floci's S3 with signature verification: the PC uploads through
 * the pre-signed PUT URL, the API reads the size, the app reads through the GET URL, and files are
 * deleted in a batch.
 */
@Tag("integration")
@Testcontainers
class AlmacenamientoDeMovimientosEnS3IntegrationTest {

    @Container
    static final GenericContainer<?> FLOCI = Floci.contenedor().withEnv("FLOCI_AUTH_VALIDATE_SIGNATURES", "true");

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static AlmacenamientoDeMovimientosEnS3 almacenamiento;

    @BeforeAll
    static void conectar() {
        URI endpoint = Floci.endpoint(FLOCI);
        almacenamiento = AlmacenamientoDeMovimientosEnS3.crear(
                new PropiedadesDeAlmacenamientoDeMovimientos(
                        "te-tengo-movimientos", "us-east-1", endpoint, true, Floci.CLAVE, Floci.CLAVE, true),
                Clock.systemUTC());
    }

    @AfterAll
    static void cerrar() {
        almacenamiento.close();
    }

    private static void subir(String clave, String contenido) throws Exception {
        var subida = almacenamiento.urlDeSubida(
                clave, "application/vnd.apache.parquet", Instant.now().plus(Duration.ofMinutes(10)));
        HttpRequest.Builder put =
                HttpRequest.newBuilder(subida.url()).PUT(HttpRequest.BodyPublishers.ofString(contenido));
        subida.cabeceras().forEach(put::header);
        assertThat(HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);
    }

    @Test
    void laPcSubeConLaUrlFirmadaYLaAppLee() throws Exception {
        String clave = "movimientos/h1/c1/2026/10/10/15/40.parquet";
        assertThat(almacenamiento.tamano(clave)).isEmpty();

        subir(clave, "parquet de prueba");

        assertThat(almacenamiento.tamano(clave)).contains((long) "parquet de prueba".length());
        var lectura = HTTP.send(
                HttpRequest.newBuilder(
                                almacenamiento.urlDeLectura(clave, Instant.now().plus(Duration.ofMinutes(5))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(lectura.statusCode()).isEqualTo(200);
        assertThat(lectura.body()).isEqualTo("parquet de prueba");
    }

    @Test
    void seBorranVariosArchivosALaVez() throws Exception {
        List<String> claves =
                List.of("movimientos/h2/c1/2026/10/10/15/40.parquet", "movimientos/h2/c1/2026/10/10/15/45.parquet");
        for (String clave : claves) {
            subir(clave, "x");
        }

        almacenamiento.eliminar(claves);
        almacenamiento.eliminar(List.of("movimientos/h2/no-existe.parquet"));

        assertThat(claves)
                .allSatisfy(clave -> assertThat(almacenamiento.tamano(clave)).isEmpty());
    }
}
