package tech.tetengo.api.versiones.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.versiones.application.port.FuenteDeManifiestos;
import tech.tetengo.api.versiones.application.port.FuenteDeManifiestos.Lectura;
import tech.tetengo.api.versiones.domain.model.Canal;
import tech.tetengo.api.versiones.domain.model.EntradaDeManifiesto;
import tech.tetengo.api.versiones.domain.model.VersionPublicada;
import tech.tetengo.api.versiones.domain.model.Versiones;

/** US-43: the versions come from the manifests the release workflows publish, cached. */
class ConsultarVersionesTest {

    static final String BASE = "https://descargas.example/versiones/";
    static final String SHA = "b".repeat(64);

    /** Answers what each test sets per channel; a channel never set has no manifest (404). */
    static final class FuenteFalsa implements FuenteDeManifiestos {
        final Map<URI, Lectura> respuestas = new HashMap<>();
        final List<URI> leidos = new ArrayList<>();

        @Override
        public Lectura leer(URI manifiesto) {
            leidos.add(manifiesto);
            return respuestas.getOrDefault(manifiesto, new Lectura.SinPublicar());
        }

        void publicar(Canal canal, EntradaDeManifiesto entrada) {
            respuestas.put(uri(canal), new Lectura.Publicada(entrada));
        }

        void fallar(Canal canal) {
            respuestas.put(uri(canal), new Lectura.Fallida());
        }

        void quitar(Canal canal) {
            respuestas.remove(uri(canal));
        }

        static URI uri(Canal canal) {
            return URI.create(BASE + canal.clave() + ".json");
        }
    }

    static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-10-11T15:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    FuenteFalsa fuente;
    Reloj reloj;

    @BeforeEach
    void preparar() {
        fuente = new FuenteFalsa();
        reloj = new Reloj();
    }

    private ConsultarVersiones consultar(String respaldo, Map<String, String> minimas) {
        return new ConsultarVersiones(
                fuente,
                new PropiedadesDeVersiones(
                        "https://descargas.example/",
                        URI.create("https://tetengo.example/descargas"),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(5),
                        respaldo,
                        minimas),
                reloj);
    }

    private static EntradaDeManifiesto instalador(String version) {
        return new EntradaDeManifiesto(
                version,
                "https://descargas.example/te-tengo-captura-setup.exe",
                SHA,
                90_177_536L,
                "2026-09-22T08:00:00Z",
                List.of("Detecta mejor las caídas con poca luz."));
    }

    private static EntradaDeManifiesto web(String version) {
        return new EntradaDeManifiesto(version, "https://app.example/", null, null, null, List.of());
    }

    private static EntradaDeManifiesto apkSinSuma(String version) {
        return new EntradaDeManifiesto(version, "https://d.example/te-tengo.apk", null, null, null, null);
    }

    private static String version(Versiones versiones, Canal canal) {
        return versiones.canal(canal).map(v -> v.version().toString()).orElse(null);
    }

    @Test
    void ca43_1_leeElManifiestoDeCadaCanalYDaLaPaginaDeDescargas() {
        fuente.publicar(Canal.WINDOWS, instalador("0.5.0"));
        fuente.publicar(Canal.PWA, web("0.6.0"));
        fuente.publicar(Canal.APK, instalador("0.6.0"));

        Versiones versiones = consultar(null, Map.of()).consultar();

        assertThat(version(versiones, Canal.WINDOWS)).isEqualTo("0.5.0");
        assertThat(version(versiones, Canal.PWA)).isEqualTo("0.6.0");
        assertThat(version(versiones, Canal.APK)).isEqualTo("0.6.0");
        assertThat(versiones.canal(Canal.MAC)).isEmpty();
        assertThat(versiones.canal(Canal.WINDOWS).map(VersionPublicada::sha256)).contains(SHA);
        assertThat(versiones.paginaDescargas()).hasToString("https://tetengo.example/descargas");
        assertThat(fuente.leidos)
                .containsExactly(
                        Arrays.stream(Canal.values()).map(FuenteFalsa::uri).toArray(URI[]::new));
    }

    @Test
    void losManifiestosSeLeenDeNuevoSoloCuandoPasaLaVigencia() {
        fuente.publicar(Canal.WINDOWS, instalador("0.5.0"));
        ConsultarVersiones versiones = consultar(null, Map.of());

        versiones.consultar();
        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(4));
        fuente.publicar(Canal.WINDOWS, instalador("0.5.1"));
        assertThat(version(versiones.consultar(), Canal.WINDOWS)).isEqualTo("0.5.0");
        assertThat(fuente.leidos).hasSize(6);

        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(1));
        assertThat(version(versiones.consultar(), Canal.WINDOWS)).isEqualTo("0.5.1");
        assertThat(fuente.leidos).hasSize(12);
    }

    @Test
    void unManifiestoQueNoSePuedeLeerOQueNoEsValidoConservaLoUltimoLeido() {
        fuente.publicar(Canal.WINDOWS, instalador("0.5.0"));
        fuente.publicar(Canal.APK, instalador("0.6.0"));
        fuente.publicar(Canal.PWA, web("0.6.0"));
        ConsultarVersiones versiones = consultar(null, Map.of());
        versiones.consultar();

        fuente.fallar(Canal.WINDOWS);
        fuente.publicar(Canal.APK, apkSinSuma("0.7.0"));
        fuente.quitar(Canal.PWA);
        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(10));
        Versiones despues = versiones.consultar();

        assertThat(version(despues, Canal.WINDOWS)).isEqualTo("0.5.0");
        assertThat(version(despues, Canal.APK)).isEqualTo("0.6.0");
        // A missing manifest (404) means nothing is published there any more.
        assertThat(despues.canal(Canal.PWA)).isEmpty();
    }

    @Test
    void sinNadaPublicadoTeTengoCapturaUsaElAjusteDeRespaldo() {
        Versiones versiones = consultar("0.4.1", Map.of()).consultar();

        assertThat(version(versiones, Canal.WINDOWS)).isEqualTo("0.4.1");
        assertThat(version(versiones, Canal.MAC)).isEqualTo("0.4.1");
        assertThat(versiones.canal(Canal.WINDOWS).map(VersionPublicada::url)).isEmpty();
        assertThat(versiones.canal(Canal.APK)).isEmpty();
    }

    @Test
    void elManifiestoGanaAlRespaldo() {
        fuente.publicar(Canal.WINDOWS, instalador("0.5.0"));
        ConsultarVersiones versiones = consultar("0.4.1", Map.of());

        assertThat(version(versiones.consultar(), Canal.WINDOWS)).isEqualTo("0.5.0");
        assertThat(versiones.ultimaDeEscritorio()).contains("0.5.0");
        assertThat(version(versiones.consultar(), Canal.MAC)).isEqualTo("0.4.1");
    }

    @Test
    void ca43_8_cadaCanalLlevaLaVersionMinimaConfiguradaSinPasarLaPublicada() {
        fuente.publicar(Canal.WINDOWS, instalador("0.5.0"));
        fuente.publicar(Canal.APK, instalador("0.6.0"));
        fuente.publicar(Canal.PWA, web("0.6.0"));

        Versiones versiones = consultar(null, Map.of("windows", "0.4.0", "apk", "0.9.0", "pwa", "no es versión"))
                .consultar();

        assertThat(versiones.canal(Canal.WINDOWS).orElseThrow().versionMinima()).hasToString("0.4.0");
        assertThat(versiones.canal(Canal.APK).orElseThrow().versionMinima()).hasToString("0.6.0");
        assertThat(versiones.canal(Canal.PWA).orElseThrow().versionMinima()).isNull();
    }

    @Test
    void unCanalInvalidoNoSePublicaYLosDemasSi() {
        fuente.publicar(Canal.APK, apkSinSuma("0.6.0"));
        fuente.publicar(Canal.PWA, web("0.6.0"));

        Versiones versiones = consultar(null, Map.of()).consultar();

        assertThat(versiones.canal(Canal.APK)).isEmpty();
        assertThat(version(versiones, Canal.PWA)).isEqualTo("0.6.0");
    }

    @Test
    void sinDireccionBaseNoSeLeeNingunManifiesto() {
        ConsultarVersiones versiones =
                new ConsultarVersiones(fuente, new PropiedadesDeVersiones(" ", null, null, null, null, null), reloj);

        Versiones v = versiones.consultar();

        assertThat(v.canales()).isEmpty();
        assertThat(v.paginaDescargas()).isNull();
        assertThat(versiones.ultimaDeEscritorio()).isEmpty();
        assertThat(fuente.leidos).isEmpty();
    }
}
