package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** CA-28.6: 5 wrong codes in a row block code entry for 15 minutes. */
class IntentosDeVinculacionTest {

    private static final Instant T0 = Instant.parse("2026-10-10T15:00:00Z");

    @Test
    void elQuintoFalloBloquea15Minutos() {
        var intentos = new IntentosDeVinculacion(UUID.randomUUID());
        assertThat(intentos.registrarFallo(T0)).isEqualTo(4);
        intentos.registrarFallo(T0);
        intentos.registrarFallo(T0);
        assertThat(intentos.registrarFallo(T0)).isEqualTo(1);
        assertThat(intentos.registrarFallo(T0)).isZero();
        assertThat(intentos.bloqueada(T0.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(intentos.getBloqueadaHasta()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
        assertThat(intentos.bloqueada(T0.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void trasElBloqueoLaCuentaEmpiezaDeNuevoYUnCodigoQueExisteLaReinicia() {
        var intentos = new IntentosDeVinculacion(UUID.randomUUID());
        for (int i = 0; i < 5; i++) {
            intentos.registrarFallo(T0);
        }
        assertThat(intentos.registrarFallo(T0.plus(Duration.ofMinutes(16)))).isEqualTo(4);
        intentos.reiniciar();
        assertThat(intentos.getFallidos()).isZero();
        assertThat(intentos.registrarFallo(T0.plus(Duration.ofMinutes(17)))).isEqualTo(4);
    }
}
