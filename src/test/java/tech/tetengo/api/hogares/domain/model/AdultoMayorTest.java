package tech.tetengo.api.hogares.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdultoMayorTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"Rosa Huamán|Rosa", "Rosa|Rosa", "'  María  del Carmen  Quispe '|María"})
    void elNombreDePilaEsLaPrimeraPalabraDelNombre(String nombre, String esperado) {
        assertThat(new AdultoMayor(nombre, 78, "Lima", Convivencia.SOLO, null).nombreDePila())
                .isEqualTo(esperado);
    }
}
