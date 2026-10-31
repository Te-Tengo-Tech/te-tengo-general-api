package tech.tetengo.api.movimientos.infrastructure.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos.Subida;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.CuentaDeAzureBlob;
import tech.tetengo.api.support.Azurite;

/**
 * The movement files' Azure Blob adapter against Azurite: the PC uploads through the SAS URL, the size
 * is read back, and deleting hundreds of blobs at once deletes every existing one and ignores missing ones.
 */
@Tag("integration")
@Testcontainers
class AlmacenamientoDeMovimientosEnAzureIntegrationTest {

    @Container
    static final GenericContainer<?> AZURITE = Azurite.contenedor();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static AlmacenamientoDeMovimientosEnAzure almacenamiento;

    @BeforeAll
    static void conectar() {
        almacenamiento = new AlmacenamientoDeMovimientosEnAzure(
                CuentaDeAzureBlob.crear(Azurite.propiedades(AZURITE), Clock.systemUTC())
                        .contenedor("movements"));
    }

    private static void subir(String clave, String cuerpo) throws Exception {
        Subida subida = almacenamiento.urlDeSubida(
                clave, "application/vnd.apache.parquet", Instant.now().plus(Duration.ofMinutes(10)));
        HttpRequest.Builder put = HttpRequest.newBuilder(subida.url()).PUT(HttpRequest.BodyPublishers.ofString(cuerpo));
        subida.cabeceras().forEach(put::header);
        assertThat(HTTP.send(put.build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(201);
    }

    @Test
    void elPcSubeElArchivoYSuTamanoSeLee() throws Exception {
        String clave = "movimientos/h1/c1/2026/10/31/12/05.parquet";
        assertThat(almacenamiento.tamano(clave)).isEmpty();

        subir(clave, "parquet de prueba");

        assertThat(almacenamiento.tamano(clave)).hasValue(17L);
        HttpResponse<String> lectura = HTTP.send(
                HttpRequest.newBuilder(
                                almacenamiento.urlDeLectura(clave, Instant.now().plusSeconds(600)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(lectura.statusCode()).isEqualTo(200);
        assertThat(lectura.body()).isEqualTo("parquet de prueba");
    }

    @Test
    void eliminaTodosLosQueExistenEIgnoraLosQueNoExisten() throws Exception {
        List<String> claves = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            claves.add("movimientos/h2/c1/2026/10/31/%02d/%02d.parquet".formatted(i / 60, i % 60));
        }
        subir(claves.getFirst(), "a");
        subir(claves.get(257), "b");
        subir(claves.getLast(), "c");

        almacenamiento.eliminar(claves);

        assertThat(almacenamiento.tamano(claves.getFirst())).isEmpty();
        assertThat(almacenamiento.tamano(claves.get(257))).isEmpty();
        assertThat(almacenamiento.tamano(claves.getLast())).isEmpty();
        almacenamiento.eliminar(List.of(claves.getFirst()));
    }
}
