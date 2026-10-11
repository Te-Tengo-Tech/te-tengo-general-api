package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-29 / CA-29.2: room names. */
class AmbienteTest {

    private static final Instant T0 = Instant.parse("2026-10-10T15:00:00Z");

    @Test
    void elNombreEsObligatorioDeHasta40CaracteresYSeComparaSinMayusculas() {
        assertThat(new Ambiente("  Sala   comedor ").getNombre()).isEqualTo("Sala comedor");
        assertThat(Ambiente.normalizar("SALA  Comedor")).isEqualTo("sala comedor");
        assertThatThrownBy(() -> new Ambiente(" "))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessage("Escribe el nombre del ambiente.");
        assertThatThrownBy(() -> new Ambiente("x".repeat(41))).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    void renombrarDevuelveElNombreAnteriorSoloSiCambia() {
        Ambiente sala = new Ambiente("Sala");
        assertThat(sala.renombrar("Sala", UUID.randomUUID(), T0)).isNull();
        assertThat(sala.renombrar("Sala comedor", UUID.randomUUID(), T0)).isEqualTo("Sala");
        assertThat(sala.getNombreNormalizado()).isEqualTo("sala comedor");
    }

    @Test
    void unaCamaraSigueASuAmbienteYVuelveAUsarseConElMismoDispositivo() {
        Ambiente sala = new Ambiente("Sala");
        Ambiente cocina = new Ambiente("Cocina");
        Camara camara = Camara.deDispositivo(UUID.randomUUID(), UUID.randomUUID(), "usb#1", "Webcam", sala, null, T0);
        assertThat(camara.getNombreHabitacion()).isEqualTo("Sala");
        assertThat(camara.asignarAmbiente(sala, null, T0)).isFalse();
        assertThat(camara.asignarAmbiente(cocina, null, T0)).isTrue();
        assertThat(camara.getAmbienteId()).isEqualTo(cocina.getId());

        cocina.renombrar("Cocina grande", null, T0);
        camara.seguirAmbiente(cocina);
        assertThat(camara.getNombreHabitacion()).isEqualTo("Cocina grande");

        assertThat(camara.dejarDeUsar(T0)).isTrue();
        assertThat(camara.dejarDeUsar(T0)).isFalse();
        assertThat(camara.asignarAmbiente(cocina, null, T0)).isTrue();
        assertThat(camara.retirada()).isFalse();
    }
}
