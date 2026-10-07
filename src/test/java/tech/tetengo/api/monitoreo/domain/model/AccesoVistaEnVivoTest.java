package tech.tetengo.api.monitoreo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccesoVistaEnVivoTest {

    private final Instant inicio = Instant.parse("2026-10-07T15:00:00Z");

    private AccesoVistaEnVivo acceso() {
        return new AccesoVistaEnVivo(UUID.randomUUID(), UUID.randomUUID(), null, inicio, Duration.ofMinutes(5), "h");
    }

    @Test
    void ca24_1_laDuracionVaDelInicioAlCierre() {
        AccesoVistaEnVivo acceso = acceso();
        acceso.conectar(inicio.plusSeconds(2));
        acceso.cerrar(inicio.plusSeconds(125));
        assertThat(acceso.duracionSegundos(inicio.plusSeconds(999))).isEqualTo(125);
    }

    @Test
    void unaSesionAbiertaCuentaHastaAhora() {
        AccesoVistaEnVivo acceso = acceso();
        acceso.conectar(inicio.plusSeconds(1));
        assertThat(acceso.duracionSegundos(inicio.plus(Duration.ofMinutes(20)))).isEqualTo(1200);
    }

    @Test
    void unaSesionQueNuncaSeConectoTerminaAlVencerSuEnlace() {
        assertThat(acceso().duracionSegundos(inicio.plus(Duration.ofHours(1)))).isEqualTo(300);
    }

    @Test
    void elEnlaceSirveUnaSolaVezYAntesDeVencer() {
        AccesoVistaEnVivo acceso = acceso();
        assertThat(acceso.conectar(inicio.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(acceso.conectar(inicio.plusSeconds(10))).isTrue();
        assertThat(acceso.conectar(inicio.plusSeconds(20))).isFalse();
    }
}
