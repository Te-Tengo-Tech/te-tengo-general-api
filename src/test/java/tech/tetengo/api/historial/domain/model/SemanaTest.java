package tech.tetengo.api.historial.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

class SemanaTest {

    private final ZoneId lima = ZoneId.of("America/Lima");

    @Test
    void laSemanaIsoVaDeLunesALunesEnLaZonaDelHogar() {
        Semana semana = Semana.desde("2026-W41");
        assertThat(semana.inicio(lima)).isEqualTo(Instant.parse("2026-10-05T05:00:00Z"));
        assertThat(semana.fin(lima)).isEqualTo(Instant.parse("2026-10-12T05:00:00Z"));
        assertThat(semana).hasToString("2026-W41");
        assertThat(semana.anterior()).hasToString("2026-W40");
    }

    @Test
    void laSemanaDeUnInstanteSeCalculaEnLima() {
        // Monday 2026-10-12 02:00Z is still Sunday in Lima: week 41.
        assertThat(Semana.de(Instant.parse("2026-10-12T02:00:00Z"), lima)).hasToString("2026-W41");
        assertThat(Semana.de(Instant.parse("2026-10-12T06:00:00Z"), lima)).hasToString("2026-W42");
    }

    @Test
    void laSemanaAnteriorPuedeSerDelAnioAnterior() {
        assertThat(Semana.desde("2027-W01").anterior()).hasToString("2026-W53");
    }

    @Test
    void rechazaSemanasInexistentesOMalEscritas() {
        for (String texto : new String[] {"2026-41", "2026-W00", "2025-W53", "semana", ""}) {
            assertThatThrownBy(() -> Semana.desde(texto)).isInstanceOf(ErrorDeNegocio.class);
        }
        assertThat(Semana.desde("2026-W53")).hasToString("2026-W53");
    }

    @Test
    void ca27_3_laTendenciaComparaConLaSemanaAnterior() {
        assertThat(Tendencia.comparar(3, 1)).isEqualTo(Tendencia.AUMENTO);
        assertThat(Tendencia.comparar(2, 2)).isEqualTo(Tendencia.IGUAL);
        assertThat(Tendencia.comparar(0, 4)).isEqualTo(Tendencia.DISMINUCION);
    }
}
