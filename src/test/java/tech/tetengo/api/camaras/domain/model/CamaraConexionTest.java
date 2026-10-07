package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CamaraConexionTest {

    private final Instant ahora = Instant.parse("2026-10-07T15:00:00Z");

    @Test
    void ca07_2_sinSenalDesdeElLimiteSeDesconectaUnaSolaVez() {
        Camara camara = new Camara("Sala");
        camara.registrarSenal(ahora);
        Instant limite = ahora.plusSeconds(1);

        assertThat(camara.desconectarSiNoHaySenalDesde(limite, ahora.plusSeconds(91)))
                .isTrue();
        assertThat(camara.getEstadoConexion()).isEqualTo(EstadoConexion.DESCONECTADA);
        assertThat(camara.desconectarSiNoHaySenalDesde(limite, ahora.plusSeconds(92)))
                .isFalse();
    }

    @Test
    void conSenalRecienteSigueEnLinea() {
        Camara camara = new Camara("Sala");
        camara.registrarSenal(ahora);
        assertThat(camara.desconectarSiNoHaySenalDesde(ahora.minus(Duration.ofSeconds(90)), ahora))
                .isFalse();
        assertThat(camara.getEstadoConexion()).isEqualTo(EstadoConexion.EN_LINEA);
    }

    @Test
    void unaCamaraQueNuncaEnvioSenalNoSeDaPorDesconectada() {
        Camara camara = new Camara("Sala");
        assertThat(camara.desconectarSiNoHaySenalDesde(ahora, ahora)).isFalse();
    }
}
