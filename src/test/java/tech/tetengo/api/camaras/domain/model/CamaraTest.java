package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

class CamaraTest {

    @Test
    void nuevaCamaraEmpiezaDesconectadaYConNombreLimpio() {
        Camara camara = new Camara("  Sala  ");
        assertThat(camara.getNombreHabitacion()).isEqualTo("Sala");
        assertThat(camara.getEstadoConexion()).isEqualTo(EstadoConexion.DESCONECTADA);
        assertThat(camara.getId()).isNotNull();
        assertThat(camara.getPausadaHasta()).isNull();
        assertThat(camara.isDeteccionConfiable()).isTrue();
    }

    @Test
    void noAceptaNombreVacio() {
        Camara camara = new Camara("Sala");
        assertThatThrownBy(() -> camara.renombrar("   "))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(CamaraError.NOMBRE_VACIO);
    }

    @Test
    void laSenalLaPoneEnLinea() {
        Camara camara = new Camara("Sala");
        Instant ahora = Instant.now();
        camara.registrarSenal(ahora);
        assertThat(camara.getEstadoConexion()).isEqualTo(EstadoConexion.EN_LINEA);
        assertThat(camara.getUltimaSenal()).isEqualTo(ahora);
    }
}
