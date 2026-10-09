package tech.tetengo.api.monitoreo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransmisionEnVivoTest {

    private final TransmisionEnVivo transmision =
            new TransmisionEnVivo(UUID.randomUUID(), "a", ModoDeVista.VIDEO, Instant.parse("2026-10-07T15:00:00Z"));

    @Test
    void cambiaDeModoSoloSiEsOtro() {
        assertThat(transmision.cambiarModo(null)).isFalse();
        assertThat(transmision.cambiarModo(ModoDeVista.VIDEO)).isFalse();
        assertThat(transmision.cambiarModo(ModoDeVista.SOLO_POSTURA)).isTrue();
        assertThat(transmision.getModo()).isEqualTo(ModoDeVista.SOLO_POSTURA);
    }

    @Test
    void unaClaveNuevaReemplazaALaAnterior() {
        transmision.renovarClave("b");
        assertThat(transmision.getClaveHash()).isEqualTo("b");
    }

    @Test
    void losModosSonLosDelContrato() {
        assertThat(ModoDeVista.desde("VIDEO_CON_POSTURA")).contains(ModoDeVista.VIDEO_CON_POSTURA);
        assertThat(ModoDeVista.desde("video")).isEmpty();
        assertThat(ModoDeVista.desde(null)).isEmpty();
        assertThat(ModoDeVista.PREDETERMINADO).isEqualTo(ModoDeVista.VIDEO);
    }
}
