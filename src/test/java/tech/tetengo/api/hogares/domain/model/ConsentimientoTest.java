package tech.tetengo.api.hogares.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

class ConsentimientoTest {

    private final Instant ahora = Instant.parse("2026-10-07T15:00:00Z");

    @Test
    void ca05_3_guardaLaFechaYHoraEnQueSeOtorgo() {
        Consentimiento c = Consentimiento.otorgar(UUID.randomUUID(), " Rosa ", UUID.randomUUID(), true, true, ahora);
        assertThat(c.getOtorgadoEn()).isEqualTo(ahora);
        assertThat(c.getOtorgadoPor()).isEqualTo("Rosa");
        assertThat(c.vigente()).isTrue();
    }

    @Test
    void ca05_4_soloSeRegistraSiElAdultoMayorAceptaIncluidaLaVistaEnVivo() {
        for (Boolean[] banderas : new Boolean[][] {{false, true}, {true, false}, {null, true}, {true, null}}) {
            assertThatThrownBy(() -> Consentimiento.otorgar(
                            UUID.randomUUID(), "Rosa", UUID.randomUUID(), banderas[0], banderas[1], ahora))
                    .extracting(e -> ((ErrorDeNegocio) e).error())
                    .isEqualTo(HogarError.CONSENTIMIENTO_NO_ACEPTADO);
        }
    }

    @Test
    void unConsentimientoNuevoReemplazaAlAnterior() {
        Consentimiento c = Consentimiento.otorgar(UUID.randomUUID(), "Rosa", UUID.randomUUID(), true, true, ahora);
        c.reemplazar(ahora);
        assertThat(c.vigente()).isFalse();
    }
}
