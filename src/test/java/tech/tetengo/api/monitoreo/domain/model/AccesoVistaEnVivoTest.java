package tech.tetengo.api.monitoreo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccesoVistaEnVivoTest {

    private static final Duration INACTIVIDAD = Duration.ofSeconds(30);

    private final Instant inicio = Instant.parse("2026-10-07T15:00:00Z");

    private AccesoVistaEnVivo acceso() {
        return new AccesoVistaEnVivo(UUID.randomUUID(), UUID.randomUUID(), null, inicio, Duration.ofMinutes(10), "h");
    }

    @Test
    void ca24_1_laDuracionVaDelInicioAlCierre() {
        AccesoVistaEnVivo acceso = acceso();
        acceso.registrarLectura(inicio.plusSeconds(2));
        assertThat(acceso.finalizar(inicio.plusSeconds(125))).isTrue();
        assertThat(acceso.duracionSegundos(inicio.plusSeconds(999))).isEqualTo(125);
        assertThat(acceso.finalizar(inicio.plusSeconds(300))).isFalse();
        assertThat(acceso.getFin()).isEqualTo(inicio.plusSeconds(125));
    }

    @Test
    void unaSesionAbiertaCuentaHastaAhoraSinPasarSuDuracionMaxima() {
        AccesoVistaEnVivo acceso = acceso();
        assertThat(acceso.duracionSegundos(inicio.plusSeconds(90))).isEqualTo(90);
        assertThat(acceso.duracionSegundos(inicio.plus(Duration.ofHours(1)))).isEqualTo(600);
    }

    @Test
    void admiteLecturasMientrasEstaAbiertaYAntesDeSuDuracionMaxima() {
        AccesoVistaEnVivo acceso = acceso();
        assertThat(acceso.getExpiraEn()).isEqualTo(inicio.plus(Duration.ofMinutes(10)));
        assertThat(acceso.admiteLectura(inicio.plusSeconds(10))).isTrue();
        assertThat(acceso.admiteLectura(inicio.plus(Duration.ofMinutes(10)))).isFalse();
        acceso.finalizar(inicio.plusSeconds(20));
        assertThat(acceso.admiteLectura(inicio.plusSeconds(30))).isFalse();
        assertThat(acceso.abierta()).isFalse();
    }

    @Test
    void laPrimeraLecturaMarcaLaConexionYCadaLecturaLaActividad() {
        AccesoVistaEnVivo acceso = acceso();
        acceso.registrarLectura(inicio.plusSeconds(3));
        acceso.registrarLectura(inicio.plusSeconds(40));
        acceso.registrarActividad(inicio.plusSeconds(20));
        assertThat(acceso.getConectadaEn()).isEqualTo(inicio.plusSeconds(3));
        assertThat(acceso.getUltimaActividad()).isEqualTo(inicio.plusSeconds(40));
    }

    @Test
    void terminaCuandoSuEspectadorDejaDeLeerEnElUltimoMomentoQueLeyo() {
        AccesoVistaEnVivo acceso = acceso();
        acceso.registrarLectura(inicio.plusSeconds(5));
        acceso.registrarActividad(inicio.plusSeconds(70));
        assertThat(acceso.finPendiente(inicio.plusSeconds(99), INACTIVIDAD)).isEmpty();
        assertThat(acceso.finPendiente(inicio.plusSeconds(100), INACTIVIDAD)).contains(inicio.plusSeconds(70));
    }

    @Test
    void unaSesionQueNuncaLeyoTerminaSinDuracion() {
        AccesoVistaEnVivo acceso = acceso();
        assertThat(acceso.finPendiente(inicio.plusSeconds(29), INACTIVIDAD)).isEmpty();
        assertThat(acceso.finPendiente(inicio.plusSeconds(30), INACTIVIDAD)).contains(inicio);
        acceso.finalizar(inicio);
        assertThat(acceso.duracionSegundos(inicio.plusSeconds(60))).isZero();
    }

    @Test
    void terminaAlLlegarASuDuracionMaxima() {
        AccesoVistaEnVivo acceso = acceso();
        Instant maximo = inicio.plus(Duration.ofMinutes(10));
        acceso.registrarActividad(maximo.plusSeconds(5));
        assertThat(acceso.finPendiente(maximo.plusSeconds(5), INACTIVIDAD)).contains(maximo);
        acceso.finalizar(maximo.plusSeconds(5));
        assertThat(acceso.getFin()).isEqualTo(maximo);
        assertThat(acceso.finPendiente(maximo.plusSeconds(60), INACTIVIDAD)).isEmpty();
    }

    @Test
    void elFinNuncaEsAnteriorAlInicio() {
        AccesoVistaEnVivo acceso = acceso();
        acceso.finalizar(inicio.minusSeconds(5));
        assertThat(acceso.getFin()).isEqualTo(inicio);
    }

    @Test
    void unaSesionCerradaNoRegistraMasActividad() {
        AccesoVistaEnVivo acceso = acceso();
        acceso.finalizar(inicio.plusSeconds(10));
        acceso.registrarActividad(inicio.plusSeconds(20));
        assertThat(acceso.getUltimaActividad()).isNull();
        assertThat(acceso.desdeAlerta()).isFalse();
    }
}
