package tech.tetengo.api.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

class RecuperacionTest {

    private final Instant ahora = Instant.parse("2026-10-07T15:00:00Z");

    @Test
    void ca03_3_elEnlaceValeTreintaMinutos() {
        Recuperacion recuperacion = new Recuperacion(UUID.randomUUID(), "h", ahora);
        assertThat(recuperacion.getExpiraEn()).isEqualTo(ahora.plus(Duration.ofMinutes(30)));

        recuperacion.usar(ahora.plus(Duration.ofMinutes(29)));
        assertThat(recuperacion.getUsadaEn()).isNotNull();
    }

    @Test
    void ca03_3_unEnlaceVencidoSeRechaza() {
        Recuperacion recuperacion = new Recuperacion(UUID.randomUUID(), "h", ahora);
        assertThatThrownBy(() -> recuperacion.usar(ahora.plus(Duration.ofMinutes(30))))
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(CuentaError.ENLACE_VENCIDO);
    }

    @Test
    void unEnlaceSoloSirveUnaVez() {
        Recuperacion recuperacion = new Recuperacion(UUID.randomUUID(), "h", ahora);
        recuperacion.usar(ahora);
        assertThatThrownBy(() -> recuperacion.usar(ahora))
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(CuentaError.ENLACE_VENCIDO);
    }
}
