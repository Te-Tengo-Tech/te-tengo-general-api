package tech.tetengo.api.movimientos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-35, US-37, US-39: the rules of the movement data. */
class MovimientosDominioTest {

    private static final Instant T0 = Instant.parse("2026-10-10T15:42:00Z");

    @Test
    void ca35_2_soloSeRegistraAceptadoYConLaVersionVigente() {
        UUID hogar = UUID.randomUUID();
        var consentimiento = ConsentimientoDeMovimientos.otorgar(
                hogar, " Rosa Huamán ", false, true, UUID.randomUUID(), "1", "1", T0);
        assertThat(consentimiento.getOtorgadoPor()).isEqualTo("Rosa Huamán");
        assertThat(consentimiento.vigente()).isTrue();
        assertThatThrownBy(() -> ConsentimientoDeMovimientos.otorgar(
                        hogar, "Rosa", false, false, UUID.randomUUID(), "1", "1", T0))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThatThrownBy(() -> ConsentimientoDeMovimientos.otorgar(
                        hogar, "Rosa", false, true, UUID.randomUUID(), "2", "1", T0))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(consentimiento.revocar(UUID.randomUUID(), ConsentimientoDeMovimientos.DESDE_LA_APP, T0))
                .isTrue();
        assertThat(consentimiento.revocar(null, ConsentimientoDeMovimientos.DESDE_LA_APP, T0))
                .isFalse();
        assertThat(consentimiento.vigente()).isFalse();
    }

    @Test
    void ca37_1_laRetencionVieneApagadaYSoloAceptaLosPlazosDeLaLista() {
        var configuracion = new ConfiguracionDeMovimientos();
        assertThat(configuracion.retencion()).isEmpty();
        assertThat(configuracion.cambiarRetencion(30, UUID.randomUUID(), T0)).isTrue();
        assertThat(configuracion.cambiarRetencion(30, UUID.randomUUID(), T0)).isFalse();
        assertThat(configuracion.retencion()).contains(Duration.ofDays(30));
        assertThatThrownBy(() -> configuracion.cambiarRetencion(400, null, T0)).isInstanceOf(ErrorDeNegocio.class);
        configuracion.apagar(T0);
        assertThat(configuracion.getRetencionDias()).isNull();
    }

    @Test
    void ca37_2_laClaveSigueLaRutaDeHogarCamaraYFechaEnUtc() {
        UUID hogar = UUID.randomUUID();
        UUID camara = UUID.randomUUID();
        assertThat(ArchivoDeMovimiento.clave(hogar, camara, T0))
                .isEqualTo("movimientos/%s/%s/2026/10/10/15/42.parquet".formatted(hogar, camara));
        var archivo = new ArchivoDeMovimiento(hogar, camara, T0, T0.plusSeconds(300), 10, "a".repeat(64), 2400);
        assertThat(archivo.disponible()).isFalse();
        archivo.confirmar(9, T0);
        assertThat(archivo.disponible()).isTrue();
        assertThat(archivo.getTamanoBytes()).isEqualTo(9);
        var resumen = new ResumenDiarioDeMovimiento(camara, java.time.LocalDate.of(2026, 10, 10));
        resumen.sumar(archivo);
        assertThat(resumen.getSegundos()).isEqualTo(300);
        assertThat(resumen.getFotogramas()).isEqualTo(2400);
    }

    @Test
    void ca39_3_unaRepeticionCuentaHastaQueSeCierraYComoMaximoUnaHora() {
        UUID usuario = UUID.randomUUID();
        var repeticion = new RepeticionDeMovimiento(
                UUID.randomUUID(), usuario, T0, T0.plusSeconds(600), T0, Duration.ofHours(1));
        assertThat(repeticion.abiertaPara(usuario, T0.plusSeconds(10))).isTrue();
        assertThat(repeticion.abiertaPara(UUID.randomUUID(), T0.plusSeconds(10)))
                .isFalse();
        assertThat(repeticion.duracionSegundos(T0.plus(Duration.ofHours(3)))).isEqualTo(3600);
        repeticion.ampliar(T0.minusSeconds(60), T0.plusSeconds(900));
        assertThat(repeticion.getMomentoDesde()).isEqualTo(T0.minusSeconds(60));
        assertThat(repeticion.finalizar(T0.plusSeconds(130))).isTrue();
        assertThat(repeticion.finalizar(T0.plusSeconds(200))).isFalse();
        assertThat(repeticion.duracionSegundos(T0.plus(Duration.ofHours(3)))).isEqualTo(130);
    }
}
