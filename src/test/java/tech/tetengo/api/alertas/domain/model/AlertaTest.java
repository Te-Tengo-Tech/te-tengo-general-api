package tech.tetengo.api.alertas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertaTest {

    private final Instant ahora = Instant.parse("2026-10-07T15:04:31Z");
    private final UUID camara = UUID.randomUUID();

    @Test
    void ca11_1_unaCaidaEsUnaAlertaActivaDeSeveridadAltaConHabitacionYHora() {
        Alerta alerta = Alerta.caida(camara, "Sala", ahora);
        assertThat(alerta.getTipo()).isEqualTo(TipoAlerta.CAIDA);
        assertThat(alerta.getSeveridad()).isEqualTo(Severidad.ALTA);
        assertThat(alerta.getEstado()).isEqualTo(EstadoAlerta.ACTIVA);
        assertThat(alerta.getHabitacion()).isEqualTo("Sala");
        assertThat(alerta.getOcurridaEn()).isEqualTo(ahora);
        assertThat(alerta.isConfirmada()).isFalse();
        assertThat(alerta.isOrigenInestable()).isFalse();
    }

    @Test
    void ca17_1_unMovimientoInestableEsDeSeveridadMedia() {
        Alerta alerta = Alerta.movimientoInestable(camara, "Sala", ahora);
        assertThat(alerta.getTipo()).isEqualTo(TipoAlerta.MOVIMIENTO_INESTABLE);
        assertThat(alerta.getSeveridad()).isEqualTo(Severidad.MEDIA);
    }

    @Test
    void ca17_3_unMovimientoInestablePuedeEvolucionarACaida() {
        Alerta alerta = Alerta.movimientoInestable(camara, "Sala", ahora);
        assertThat(alerta.evolucionarACaida()).isTrue();
        assertThat(alerta.getTipo()).isEqualTo(TipoAlerta.CAIDA);
        assertThat(alerta.getSeveridad()).isEqualTo(Severidad.ALTA);
        assertThat(alerta.isOrigenInestable()).isTrue();
        assertThat(alerta.evolucionarACaida()).isFalse();
    }

    @Test
    void ca13_1_laConfirmacionMantieneLaAlertaActiva() {
        Alerta alerta = Alerta.caida(camara, "Sala", ahora);
        assertThat(alerta.confirmar()).isTrue();
        assertThat(alerta.isConfirmada()).isTrue();
        assertThat(alerta.activa()).isTrue();
        assertThat(alerta.confirmar()).isFalse();
        assertThat(Alerta.movimientoInestable(camara, "Sala", ahora).confirmar())
                .isFalse();
    }

    @Test
    void ca13_2_registraLaRecuperacionUnaSolaVez() {
        Alerta alerta = Alerta.caida(camara, "Sala", ahora);
        assertThat(alerta.registrarRecuperacion(ahora.plusSeconds(10))).isTrue();
        assertThat(alerta.getRecuperadaEn()).isEqualTo(ahora.plusSeconds(10));
        assertThat(alerta.registrarRecuperacion(ahora.plusSeconds(20))).isFalse();
    }

    @Test
    void ca16_4_elEstadoDelAvisoVaDeEnviandoAReintentandoYAEntregado() {
        Alerta alerta = Alerta.caida(camara, "Sala", ahora);
        assertThat(alerta.getEstadoAviso()).isEqualTo(EstadoAviso.ENVIANDO);

        alerta.marcarAvisoPendiente();
        assertThat(alerta.getEstadoAviso()).isEqualTo(EstadoAviso.REINTENTANDO);
        assertThat(alerta.getNotificadaEn()).isNull();

        alerta.marcarNotificada(ahora.plusSeconds(40));
        assertThat(alerta.getEstadoAviso()).isEqualTo(EstadoAviso.ENTREGADO);
        assertThat(alerta.getNotificadaEn()).isEqualTo(ahora.plusSeconds(40));
    }

    @Test
    void unAvisoEntregadoNoVuelveAtras() {
        Alerta alerta = Alerta.caida(camara, "Sala", ahora);
        alerta.marcarNotificada(ahora);

        alerta.marcarAvisoPendiente();
        alerta.marcarAvisoNoEntregado();
        alerta.marcarNotificada(ahora.plusSeconds(60));

        assertThat(alerta.getEstadoAviso()).isEqualTo(EstadoAviso.ENTREGADO);
        assertThat(alerta.getNotificadaEn()).isEqualTo(ahora);
    }

    @Test
    void sinEntregaAlTerminarLosReintentosQuedaNoEntregado() {
        Alerta alerta = Alerta.caida(camara, "Sala", ahora);
        alerta.marcarAvisoPendiente();
        alerta.marcarAvisoNoEntregado();
        assertThat(alerta.getEstadoAviso()).isEqualTo(EstadoAviso.NO_ENTREGADO);
    }
}
