package tech.tetengo.api.monitoreo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DuracionDePausaTest {

    private final Instant ahora = Instant.parse("2026-10-07T15:00:00Z");

    @Test
    void lasDuracionesFijas() {
        assertThat(DuracionDePausa.MIN_30.hasta(ahora)).isEqualTo(Instant.parse("2026-10-07T15:30:00Z"));
        assertThat(DuracionDePausa.HORA_1.hasta(ahora)).isEqualTo(Instant.parse("2026-10-07T16:00:00Z"));
        assertThat(DuracionDePausa.HORAS_2.hasta(ahora)).isEqualTo(Instant.parse("2026-10-07T17:00:00Z"));
    }

    @Test
    void hastaMananaEsLaProximaSieteDeLaMananaEnLima() {
        // 10:00 in Lima (UTC-5) → tomorrow 07:00 Lima = 12:00Z.
        assertThat(DuracionDePausa.HASTA_MANANA.hasta(ahora)).isEqualTo(Instant.parse("2026-10-08T12:00:00Z"));
        // 22:30 in Lima → next day 07:00.
        assertThat(DuracionDePausa.HASTA_MANANA.hasta(Instant.parse("2026-10-08T03:30:00Z")))
                .isEqualTo(Instant.parse("2026-10-08T12:00:00Z"));
        // 05:00 in Lima → 07:00 the same morning.
        assertThat(DuracionDePausa.HASTA_MANANA.hasta(Instant.parse("2026-10-07T10:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-07T12:00:00Z"));
        // Exactly 07:00 → the next day's 07:00.
        assertThat(DuracionDePausa.HASTA_MANANA.hasta(Instant.parse("2026-10-07T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-08T12:00:00Z"));
    }

    @Test
    void soloAdmiteLasOpcionesDelPrototipo() {
        assertThat(DuracionDePausa.desde("HORA_1")).contains(DuracionDePausa.HORA_1);
        assertThat(DuracionDePausa.desde("HORAS_3")).isEmpty();
        assertThat(DuracionDePausa.desde(null)).isEmpty();
    }
}
