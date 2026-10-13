package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo.Consulta;

/** RFC 8628 §3.5: what each poll of the PC gets. */
class AutorizacionDeDispositivoTest {

    private static final Instant T0 = Instant.parse("2026-10-10T15:00:00Z");

    private static AutorizacionDeDispositivo nueva() {
        return new AutorizacionDeDispositivo(
                "d",
                "u",
                "te-tengo-captura",
                "Sala-Rosa",
                "Windows 11",
                "0.5.0",
                null,
                T0,
                Duration.ofMinutes(10),
                Duration.ofSeconds(5));
    }

    @Test
    void esperaHastaQueLaApruebenYPideIrMasLentoSiSeConsultaAntesDelIntervalo() {
        var autorizacion = nueva();
        assertThat(autorizacion.consultar(T0)).isEqualTo(Consulta.PENDIENTE);
        assertThat(autorizacion.consultar(T0.plusSeconds(4))).isEqualTo(Consulta.MAS_LENTO);
        assertThat(autorizacion.getIntervaloSegundos()).isEqualTo(10);
        assertThat(autorizacion.consultar(T0.plusSeconds(14))).isEqualTo(Consulta.PENDIENTE);
    }

    @Test
    void venceALos10Minutos() {
        var autorizacion = nueva();
        assertThat(autorizacion.resoluble(T0.plus(Duration.ofMinutes(10)))).isFalse();
        assertThat(autorizacion.consultar(T0.plus(Duration.ofMinutes(10)))).isEqualTo(Consulta.VENCIDA);
    }

    @Test
    void aprobadaSeCanjeaUnaSolaVez() {
        var autorizacion = nueva();
        autorizacion.aprobar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T0.plusSeconds(30));
        assertThat(autorizacion.resoluble(T0.plusSeconds(31))).isFalse();
        assertThat(autorizacion.consultar(T0.plusSeconds(35))).isEqualTo(Consulta.LISTA);
        assertThat(autorizacion.consultar(T0.plusSeconds(40))).isEqualTo(Consulta.YA_CANJEADA);
    }

    @Test
    void aprobadaAlFinalDeSuVigenciaSePuedeCanjearUnosMinutosMas() {
        var autorizacion = nueva();
        Instant casiVencida = T0.plus(Duration.ofMinutes(10)).minusSeconds(1);
        autorizacion.aprobar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), casiVencida);
        assertThat(autorizacion.consultar(casiVencida.plusSeconds(30))).isEqualTo(Consulta.LISTA);
    }

    @Test
    void rechazadaDaAccesoDenegadoYNoSePuedeResolverDosVeces() {
        var autorizacion = nueva();
        autorizacion.rechazar(UUID.randomUUID(), T0);
        assertThat(autorizacion.consultar(T0.plusSeconds(5))).isEqualTo(Consulta.RECHAZADA);
        assertThatThrownBy(() -> autorizacion.aprobar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T0))
                .isInstanceOf(IllegalStateException.class);
    }
}
