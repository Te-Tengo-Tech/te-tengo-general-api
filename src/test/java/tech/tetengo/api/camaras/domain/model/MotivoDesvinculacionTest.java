package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MotivoDesvinculacionTest {

    @Test
    void laPcDiceSiSeRestablecioOSeDesinstalo() {
        assertThat(MotivoDesvinculacion.desdeLaPc(null)).isEqualTo(MotivoDesvinculacion.DESDE_LA_PC);
        assertThat(MotivoDesvinculacion.desdeLaPc(" ")).isEqualTo(MotivoDesvinculacion.DESDE_LA_PC);
        assertThat(MotivoDesvinculacion.desdeLaPc("restablecida")).isEqualTo(MotivoDesvinculacion.RESTABLECIDA);
        assertThat(MotivoDesvinculacion.desdeLaPc("desinstalada")).isEqualTo(MotivoDesvinculacion.DESINSTALADA);
        assertThatThrownBy(() -> MotivoDesvinculacion.desdeLaPc("RESTABLECIDA"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soloLosMotivosDeLaPropiaPcTienenALaPcComoActor() {
        assertThat(MotivoDesvinculacion.RESTABLECIDA.desdeLaPropiaPc()).isTrue();
        assertThat(MotivoDesvinculacion.DESINSTALADA.desdeLaPropiaPc()).isTrue();
        assertThat(MotivoDesvinculacion.DESDE_LA_PC.desdeLaPropiaPc()).isTrue();
        assertThat(MotivoDesvinculacion.DESDE_LA_APP.desdeLaPropiaPc()).isFalse();
        assertThat(MotivoDesvinculacion.VENCIDA.desdeLaPropiaPc()).isFalse();
    }
}
