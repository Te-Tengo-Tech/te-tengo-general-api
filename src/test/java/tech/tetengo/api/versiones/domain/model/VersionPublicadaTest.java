package tech.tetengo.api.versiones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US-43: what a release workflow publishes is checked before any client sees it. */
class VersionPublicadaTest {

    static final String SHA = "a".repeat(64);
    static final String INSTALADOR = "https://descargas.example/te-tengo-captura-setup.exe";

    private static EntradaDeManifiesto entrada(String version, String url, String sha256) {
        return new EntradaDeManifiesto(
                version, url, sha256, 90_177_536L, "2026-09-22T08:00:00Z", List.of("Usa menos el procesador."));
    }

    @Test
    void ca43_2_unInstaladorTraeSuDireccionSuSha256YSusNotas() {
        VersionPublicada v = VersionPublicada.de(Canal.WINDOWS, entrada("0.5.0", INSTALADOR, SHA.toUpperCase()));

        assertThat(v.version().toString()).isEqualTo("0.5.0");
        assertThat(v.url()).hasToString(INSTALADOR);
        assertThat(v.sha256()).isEqualTo(SHA);
        assertThat(v.tamanoBytes()).isEqualTo(90_177_536L);
        assertThat(v.publicadaEn()).isEqualTo(Instant.parse("2026-09-22T08:00:00Z"));
        assertThat(v.notas()).containsExactly("Usa menos el procesador.");
        assertThat(v.versionMinima()).isNull();
    }

    @Test
    void ca43_2_unArchivoSinSha256OConUnoMalFormadoNoSePublica() {
        assertThatThrownBy(() -> VersionPublicada.de(Canal.WINDOWS, entrada("0.5.0", INSTALADOR, null)))
                .hasMessageContaining("sha256");
        assertThatThrownBy(() -> VersionPublicada.de(Canal.APK, entrada("0.5.0", INSTALADOR, "abc")))
                .hasMessageContaining("64 hex");
        assertThatThrownBy(() -> VersionPublicada.de(Canal.APK, entrada("0.5.0", null, SHA)))
                .hasMessageContaining("both");
    }

    @Test
    void soloSeDescargaPorHttpsSalvoDesdeLaPropiaMaquina() {
        assertThatThrownBy(
                        () -> VersionPublicada.de(Canal.APK, entrada("0.5.0", "http://descargas.example/a.apk", SHA)))
                .hasMessageContaining("https");
        assertThatThrownBy(() -> VersionPublicada.de(Canal.APK, entrada("0.5.0", "/te-tengo.apk", SHA)))
                .hasMessageContaining("https");
        assertThatThrownBy(() -> VersionPublicada.de(Canal.APK, entrada("0.5.0", "https://a b", SHA)))
                .hasMessageContaining("valid");
        assertThat(VersionPublicada.de(Canal.APK, entrada("0.5.0", "http://localhost:8080/te-tengo.apk", SHA))
                        .url())
                .isNotNull();
    }

    @Test
    void lasTiendasYLaAplicacionWebNoTienenArchivoNiSuma() {
        VersionPublicada play = VersionPublicada.de(
                Canal.PLAY, entrada("0.6.0", "https://play.google.com/store/apps/details?id=tech.tetengo", SHA));

        assertThat(play.sha256()).isNull();
        assertThat(play.tamanoBytes()).isNull();
        assertThat(play.url()).isNotNull();
    }

    @Test
    void unaVersionOFechaMalEscritasNoSePublican() {
        assertThatThrownBy(() -> VersionPublicada.de(Canal.WINDOWS, entrada("latest", INSTALADOR, SHA)))
                .hasMessageContaining("MAJOR.MINOR.PATCH");
        assertThatThrownBy(() -> VersionPublicada.de(
                        Canal.PWA, new EntradaDeManifiesto("0.6.0", null, null, null, "ayer", List.of())))
                .hasMessageContaining("publicadaEn");
        assertThatThrownBy(() -> VersionPublicada.de(
                        Canal.WINDOWS, new EntradaDeManifiesto("0.6.0", INSTALADOR, SHA, -1L, null, List.of())))
                .hasMessageContaining("negative");
    }

    @Test
    void lasNotasSonPocasLineasCortasYLimpias() {
        List<String> muchas = Collections.nCopies(12, "Arreglos en la vista en vivo.");
        VersionPublicada v = VersionPublicada.de(
                Canal.PWA,
                new EntradaDeManifiesto(
                        "0.6.0",
                        null,
                        null,
                        null,
                        null,
                        List.of("  Las alertas\nllegan más rápido.  ", "", "x".repeat(300))));
        VersionPublicada larga =
                VersionPublicada.de(Canal.PWA, new EntradaDeManifiesto("0.6.0", null, null, null, null, muchas));

        assertThat(v.notas()).containsExactly("Las alertas llegan más rápido.", "x".repeat(200));
        assertThat(larga.notas()).hasSize(10);
    }

    @Test
    void ca43_8_laMinimaNuncaSuperaLaVersionPublicada() {
        VersionPublicada v = VersionPublicada.de(Canal.APK, entrada("0.5.0", INSTALADOR, SHA));

        assertThat(v.conMinima(NumeroDeVersion.leer("0.4.0").orElseThrow()).versionMinima())
                .hasToString("0.4.0");
        assertThat(v.conMinima(NumeroDeVersion.leer("0.9.0").orElseThrow()).versionMinima())
                .hasToString("0.5.0");
        assertThat(v.conMinima(null)).isSameAs(v);
    }

    @Test
    void unRespaldoSoloTraeLaVersion() {
        VersionPublicada v =
                VersionPublicada.soloVersion(NumeroDeVersion.leer("0.4.1").orElseThrow());

        assertThat(v.url()).isNull();
        assertThat(v.sha256()).isNull();
        assertThat(v.notas()).isEmpty();
    }
}
