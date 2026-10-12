package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

class PcTest {

    private static final Instant T0 = Instant.parse("2026-10-10T15:00:00Z");

    private static Pc vinculada() {
        return Pc.vincular(UUID.randomUUID(), " Sala-Rosa ", "Windows 11 Home", "0.5.0", null, UUID.randomUUID(), T0);
    }

    @Test
    void elNombreEsObligatorioYDeHasta60Caracteres() {
        assertThat(vinculada().getNombre()).isEqualTo("Sala-Rosa");
        assertThatThrownBy(() -> vinculada().renombrar(" ", UUID.randomUUID(), T0))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> vinculada().renombrar("x".repeat(61), UUID.randomUUID(), T0))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(vinculada().renombrar("Sala-Rosa", UUID.randomUUID(), T0)).isNull();
    }

    @Test
    void seDesvinculaUnaSolaVez() {
        Pc pc = vinculada();
        assertThat(pc.desvincular(MotivoDesvinculacion.DESDE_LA_APP, UUID.randomUUID(), List.of(), List.of(), T0))
                .isTrue();
        assertThat(pc.desvincular(MotivoDesvinculacion.DESDE_LA_PC, null, List.of(), List.of(), T0))
                .isFalse();
        assertThat(pc.vinculada()).isFalse();
        assertThat(pc.getMotivoDesvinculacion()).isEqualTo(MotivoDesvinculacion.DESDE_LA_APP);
    }

    @Test
    void ca28_7_venceTras90DiasSinUsoSalvoLasInstalaciones() {
        Pc pc = vinculada();
        Instant limite = T0.plus(Duration.ofDays(90)).minus(Duration.ofDays(90));
        assertThat(pc.vencida(limite.minusSeconds(1))).isFalse();
        assertThat(pc.vencida(limite)).isTrue();
        pc.registrarUso(T0.plus(Duration.ofDays(1)));
        assertThat(pc.vencida(limite)).isFalse();
        assertThat(Pc.deInstalacion(T0).vencida(T0.plus(Duration.ofDays(400)))).isFalse();
    }

    @Test
    void losDatosQueReportaLaPcSeRecortanAUnaLinea() {
        Pc pc = vinculada();
        assertThat(pc.actualizarDatos("0.6.0\n", null)).isTrue();
        assertThat(pc.getVersionAgente()).isEqualTo("0.6.0");
        assertThat(pc.actualizarDatos("0.6.0", "   ")).isFalse();
        assertThat(pc.actualizarDatos("v".repeat(80), null)).isTrue();
        assertThat(pc.getVersionAgente()).hasSize(40);
    }
}
