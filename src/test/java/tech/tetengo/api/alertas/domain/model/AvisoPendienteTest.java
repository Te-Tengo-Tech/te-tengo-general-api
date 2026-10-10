package tech.tetengo.api.alertas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvisoPendienteTest {

    private final Instant ahora = Instant.parse("2026-10-10T15:00:00Z");
    private final Duration cada = Duration.ofSeconds(15);

    private AvisoPendiente pendiente(Duration ventana) {
        return new AvisoPendiente(
                "ALERTA_CAIDA",
                UUID.randomUUID(),
                null,
                "Sala",
                ahora,
                null,
                null,
                ahora.plus(cada),
                ahora.plus(ventana));
    }

    @Test
    void elPrimerIntentoVaEnseguidaYReservaElAvisoMientrasSeEnvia() {
        AvisoPendiente p = pendiente(Duration.ofMinutes(30));
        assertThat(p.puedeIntentarse(ahora)).isTrue();

        p.iniciarIntento(ahora, cada);

        assertThat(p.getIntentos()).isEqualTo(1);
        assertThat(p.puedeIntentarse(ahora.plusSeconds(5))).isFalse();
        assertThat(p.puedeIntentarse(ahora.plus(cada))).isTrue();
    }

    @Test
    void seReintentaHastaSuPlazoYLuegoSeAbandona() {
        AvisoPendiente p = pendiente(Duration.ofSeconds(30));
        p.iniciarIntento(ahora, cada);
        assertThat(p.fallo(ahora, cada)).isTrue();
        assertThat(p.fallo(ahora.plusSeconds(15), cada)).isTrue();
        assertThat(p.fallo(ahora.plusSeconds(30), cada)).isFalse();
    }

    @Test
    void unTelefonoQueSeRegistraLoAdelanta() {
        AvisoPendiente p = pendiente(Duration.ofMinutes(30));
        p.iniciarIntento(ahora, cada);
        p.fallo(ahora, cada);

        p.adelantar(ahora.plusSeconds(3));

        assertThat(p.getProximoIntento()).isEqualTo(ahora.plusSeconds(3));
        assertThat(p.puedeIntentarse(ahora.plusSeconds(3))).isTrue();
    }
}
