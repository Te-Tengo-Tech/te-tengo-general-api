package tech.tetengo.api.versiones.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RFC 9110: If-None-Match compares ETags weakly. */
class VersionesControllerTest {

    static final String ETAG = "\"abc\"";

    @Test
    void reconoceLaMismaEtiquetaDebilEnUnaListaOElAsterisco() {
        assertThat(VersionesController.coincide(ETAG, ETAG)).isTrue();
        assertThat(VersionesController.coincide("W/\"abc\"", ETAG)).isTrue();
        assertThat(VersionesController.coincide("\"x\", \"abc\"", ETAG)).isTrue();
        assertThat(VersionesController.coincide("*", ETAG)).isTrue();
    }

    @Test
    void otraEtiquetaONingunaNoCoinciden() {
        assertThat(VersionesController.coincide("\"x\"", ETAG)).isFalse();
        assertThat(VersionesController.coincide("", ETAG)).isFalse();
        assertThat(VersionesController.coincide(null, ETAG)).isFalse();
    }
}
