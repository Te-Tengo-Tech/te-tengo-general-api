package tech.tetengo.api.versiones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-43: versions are compared numerically by MAJOR.MINOR.PATCH. */
class NumeroDeVersionTest {

    private static NumeroDeVersion v(String texto) {
        return NumeroDeVersion.leer(texto).orElseThrow();
    }

    @Test
    void comparaNumericamenteNoComoTexto() {
        assertThat(v("0.10.0")).isGreaterThan(v("0.9.9"));
        assertThat(v("1.0.0")).isGreaterThan(v("0.99.99"));
        assertThat(v("0.4.3").esAnteriorA(v("0.5.0"))).isTrue();
        assertThat(v("0.5.0").esAnteriorA(v("0.5.0"))).isFalse();
    }

    @Test
    void ignoraLaVLosSufijosDeCompilacionYDePrelanzamiento() {
        assertThat(v("0.5.3+12")).isEqualTo(v("0.5.3"));
        assertThat(v("v0.5.0")).isEqualTo(v("0.5.0"));
        assertThat(v("0.5.0-rc.2")).isEqualTo(v("0.5.0"));
        assertThat(v(" 0.5.0 ").toString()).isEqualTo("0.5.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0.5", "0.5.x", "latest", "1.2.3.4", "-1.0.0", "0.5.0 beta"})
    void loQueNoEsMayorMenorParcheNoEsUnaVersion(String texto) {
        assertThat(NumeroDeVersion.leer(texto)).isEmpty();
    }

    @Test
    void sinTextoNoHayVersionYNoAdmiteNegativos() {
        assertThat(NumeroDeVersion.leer(null)).isEmpty();
        assertThatThrownBy(() -> new NumeroDeVersion(0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
