package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** RFC 8628 §6.1 and CA-28.2: the PC's code. */
class CodigoDeUsuarioTest {

    @Test
    void tiene8LetrasDelAlfabetoSinVocalesNiDigitosYSeMuestraConGuion() {
        var vistos = new HashSet<String>();
        for (int i = 0; i < 500; i++) {
            CodigoDeUsuario codigo = CodigoDeUsuario.generar();
            assertThat(codigo.valor()).matches("[BCDFGHJKLMNPQRSTVWXZ]{8}");
            assertThat(codigo.legible()).matches("[BCDFGHJKLMNPQRSTVWXZ]{4}-[BCDFGHJKLMNPQRSTVWXZ]{4}");
            vistos.add(codigo.valor());
        }
        assertThat(vistos).hasSizeGreaterThan(495);
    }

    @Test
    void usaTodoElAlfabeto() {
        var letras = new HashSet<Character>();
        Random aleatorio = new Random(7);
        for (int i = 0; i < 200; i++) {
            CodigoDeUsuario.generar(aleatorio).valor().chars().forEach(c -> letras.add((char) c));
        }
        assertThat(letras).hasSize(CodigoDeUsuario.ALFABETO.length());
    }

    @Test
    void ca28_2_seLeeEnMinusculasConOSinGuionYConEspacios() {
        assertThat(CodigoDeUsuario.leer("kqwt-mxhd"))
                .map(CodigoDeUsuario::valor)
                .contains("KQWTMXHD");
        assertThat(CodigoDeUsuario.leer("KQWTMXHD"))
                .map(CodigoDeUsuario::legible)
                .contains("KQWT-MXHD");
        assertThat(CodigoDeUsuario.leer(" kqwt mxhd ")).isPresent();
    }

    @Test
    void ca28_2_rechazaVocalesDigitosYLargosDistintos() {
        assertThat(CodigoDeUsuario.leer("KQ7T-MX4D")).isEmpty();
        assertThat(CodigoDeUsuario.leer("KQAT-MXHD")).isEmpty();
        assertThat(CodigoDeUsuario.leer("KQWT-MXH")).isEmpty();
        assertThat(CodigoDeUsuario.leer("KQWT-MXHDB")).isEmpty();
        assertThat(CodigoDeUsuario.leer(null)).isEmpty();
        assertThatThrownBy(() -> new CodigoDeUsuario("AAAAAAAA")).isInstanceOf(IllegalArgumentException.class);
    }
}
