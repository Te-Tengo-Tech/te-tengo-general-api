package tech.tetengo.api.versiones.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.VinculacionDePrueba;

/**
 * US-43: {@code GET /api/versiones} serves what the release workflows published in the downloads
 * bucket, one manifest per channel (here a local server), to anyone, cache-friendly.
 */
class VersionesIntegrationTest extends AbstractIntegrationTest {

    static final String SHA_WINDOWS = "d".repeat(64);
    static final String SHA_APK = "e".repeat(64);
    static final HttpServer DESCARGAS = descargas();

    @Autowired
    MockMvc mvc;

    private static HttpServer descargas() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            publicar(servidor, "/versiones/windows.json", """
                    {"version": "0.5.0", "url": "https://descargas.example/te-tengo-captura-setup.exe",
                     "sha256": "%s", "tamanoBytes": 90177536, "publicadaEn": "2026-09-22T08:00:00Z",
                     "notas": ["Detecta mejor las caídas con poca luz.", "Usa menos el procesador."]}
                    """.formatted(SHA_WINDOWS));
            publicar(servidor, "/versiones/apk.json", """
                    {"version": "0.6.0", "url": "https://descargas.example/te-tengo.apk", "sha256": "%s",
                     "tamanoBytes": 8808038, "publicadaEn": "2026-10-01T12:00:00Z",
                     "notas": ["Las alertas llegan más rápido."]}
                    """.formatted(SHA_APK));
            publicar(servidor, "/versiones/pwa.json", """
                    {"version": "0.6.0", "url": "https://app.example/", "publicadaEn": "2026-10-01T12:00:00Z",
                     "notas": ["Las alertas llegan más rápido."]}
                    """);
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void publicar(HttpServer servidor, String ruta, String cuerpo) {
        servidor.createContext(ruta, intercambio -> {
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
    }

    @DynamicPropertySource
    static void manifiestos(DynamicPropertyRegistry registro) {
        registro.add(
                "tetengo.versiones.url-base",
                () -> "http://127.0.0.1:" + DESCARGAS.getAddress().getPort() + "/");
        registro.add("tetengo.versiones.pagina-descargas", () -> "https://tetengo.example/descargas");
        registro.add("tetengo.versiones.respaldo-escritorio", () -> "0.4.1");
        registro.add("tetengo.versiones.minimas.apk", () -> "0.5.0");
    }

    @AfterAll
    static void detener() {
        DESCARGAS.stop(0);
    }

    @Test
    void ca43_1_cualquieraVeLaUltimaVersionDeCadaCanalSinIniciarSesion() throws Exception {
        mvc.perform(get("/api/versiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windows.version").value("0.5.0"))
                .andExpect(jsonPath("$.windows.url").value("https://descargas.example/te-tengo-captura-setup.exe"))
                .andExpect(jsonPath("$.windows.sha256").value(SHA_WINDOWS))
                .andExpect(jsonPath("$.windows.tamanoBytes").value(90177536))
                .andExpect(jsonPath("$.windows.publicadaEn").value("2026-09-22T08:00:00Z"))
                .andExpect(jsonPath("$.windows.notas[0]").value("Detecta mejor las caídas con poca luz."))
                .andExpect(jsonPath("$.windows.versionMinima").isEmpty())
                // CA-43.3: no macOS manifest, so only the fallback version and the downloads page.
                .andExpect(jsonPath("$.mac.version").value("0.4.1"))
                .andExpect(jsonPath("$.mac.url").isEmpty())
                .andExpect(jsonPath("$.paginaDescargas").value("https://tetengo.example/descargas"))
                .andExpect(jsonPath("$.apk.version").value("0.6.0"))
                .andExpect(jsonPath("$.apk.sha256").value(SHA_APK))
                .andExpect(jsonPath("$.pwa.sha256").isEmpty())
                .andExpect(jsonPath("$.play").isEmpty())
                .andExpect(jsonPath("$.ios").isEmpty());
    }

    @Test
    void ca43_8_cadaCanalDiceSuVersionMinima() throws Exception {
        mvc.perform(get("/api/versiones"))
                .andExpect(jsonPath("$.apk.versionMinima").value("0.5.0"))
                .andExpect(jsonPath("$.pwa.versionMinima").isEmpty());
    }

    @Test
    void laRespuestaSePuedeGuardarEnCacheYUnaCopiaVigenteNoSeVuelveAEnviar() throws Exception {
        String etag = mvc.perform(get("/api/versiones"))
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(header().exists("ETag"))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mvc.perform(get("/api/versiones").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(content().string(""));
        mvc.perform(get("/api/versiones").header("If-None-Match", "\"otra\"")).andExpect(status().isOk());
    }

    @Test
    void laConfiguracionDelAgentePublicaLaVersionDeWindows() throws Exception {
        String titular = campo(ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen"), "$.tokenAcceso");
        String pc = campo(VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa"), "$.access_token");

        mvc.perform(get("/api/agente/configuracion").header("Authorization", bearer(pc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionAgente").value("0.5.0"));
    }
}
