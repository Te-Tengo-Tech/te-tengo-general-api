package tech.tetengo.api.versiones.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.versiones.application.PropiedadesDeVersiones;
import tech.tetengo.api.versiones.application.port.FuenteDeManifiestos.Lectura;

/** ADR 0012: each channel's manifest is read over HTTP from the downloads bucket. */
class FuenteDeManifiestosHttpTest {

    HttpServer servidor;
    FuenteDeManifiestosHttp fuente;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        responder("/versiones/windows.json", 200, """
                {"version": "0.5.0", "url": "https://d.example/te-tengo-captura-setup.exe",
                 "sha256": "%s", "tamanoBytes": 90177536, "publicadaEn": "2026-09-22T08:00:00Z",
                 "notas": ["Usa menos el procesador.", 3]}
                """.formatted("c".repeat(64)));
        responder("/versiones/roto.json", 200, "{no es json");
        responder("/versiones/lista.json", 200, "[\"0.5.0\"]");
        responder("/versiones/error.json", 503, "");
        responder("/versiones/enorme.json", 200, "{\"x\": \"" + "x".repeat(70_000) + "\"}");
        servidor.start();
        fuente = new FuenteDeManifiestosHttp(
                new PropiedadesDeVersiones(null, null, null, Duration.ofSeconds(2), null, null));
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private void responder(String ruta, int estado, String cuerpo) {
        servidor.createContext(ruta, intercambio -> {
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                intercambio.getResponseBody().write(bytes);
            }
            intercambio.close();
        });
    }

    private URI uri(String ruta) {
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + ruta);
    }

    @Test
    void leeElManifiestoDelCanal() {
        Lectura lectura = fuente.leer(uri("/versiones/windows.json"));

        assertThat(lectura).isInstanceOf(Lectura.Publicada.class);
        var windows = ((Lectura.Publicada) lectura).entrada();
        assertThat(windows.version()).isEqualTo("0.5.0");
        assertThat(windows.sha256()).isEqualTo("c".repeat(64));
        assertThat(windows.tamanoBytes()).isEqualTo(90_177_536L);
        assertThat(windows.publicadaEn()).isEqualTo("2026-09-22T08:00:00Z");
        assertThat(windows.notas()).containsExactly("Usa menos el procesador.");
    }

    @Test
    void sinManifiestoNoHayNadaPublicado() {
        assertThat(fuente.leer(uri("/versiones/apk.json"))).isInstanceOf(Lectura.SinPublicar.class);
    }

    @Test
    void unErrorDelServidorOUnCuerpoInvalidoNoSeLee() {
        assertThat(fuente.leer(uri("/versiones/error.json"))).isInstanceOf(Lectura.Fallida.class);
        assertThat(fuente.leer(uri("/versiones/roto.json"))).isInstanceOf(Lectura.Fallida.class);
        assertThat(fuente.leer(uri("/versiones/lista.json"))).isInstanceOf(Lectura.Fallida.class);
        assertThat(fuente.leer(uri("/versiones/enorme.json"))).isInstanceOf(Lectura.Fallida.class);
    }

    @Test
    void sinConexionNoSeLee() {
        servidor.stop(0);

        assertThat(fuente.leer(uri("/versiones/windows.json"))).isInstanceOf(Lectura.Fallida.class);
    }
}
