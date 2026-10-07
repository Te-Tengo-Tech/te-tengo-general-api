package tech.tetengo.api.hogares.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

class HogarAvisoTest {

    private final Hogar hogar = new Hogar(UUID.randomUUID(), new AdultoMayor("Rosa", "Lima", Convivencia.SOLO));

    @Test
    void ca10_3_sinElegirSeEsperanCincoMinutos() {
        assertThat(hogar.getEsperaMinutos()).isEqualTo(5);
    }

    @Test
    void ca10_2_soloSeAdmitenTresCincoODiezMinutos() {
        for (int espera : new int[] {3, 5, 10}) {
            hogar.configurarAviso(UUID.randomUUID(), null, espera);
            assertThat(hogar.getEsperaMinutos()).isEqualTo(espera);
        }
        for (int espera : new int[] {0, 4, 15}) {
            assertThatThrownBy(() -> hogar.configurarAviso(UUID.randomUUID(), null, espera))
                    .extracting(e -> ((ErrorDeNegocio) e).error())
                    .isEqualTo(HogarError.ESPERA_INVALIDA);
        }
    }

    @Test
    void elPrincipalYElSecundarioSonDistintos() {
        UUID mismo = UUID.randomUUID();
        assertThatThrownBy(() -> hogar.configurarAviso(mismo, mismo, 5))
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(HogarError.CONTACTO_NO_ES_FAMILIAR);
    }
}
