package tech.tetengo.api.monitoreo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** What a denied attempt keeps (ADR 0015). */
class IntentoDenegadoTest {

    private static final Instant AHORA = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void laAccionYElProtocoloDeMediaMtxSeRecortanYVaciosQuedanNulos() {
        IntentoDenegado intento = new IntentoDenegado(
                AHORA,
                CanalDeAcceso.MEDIAMTX,
                MotivoDeDenegacion.ACCION_NO_PERMITIDA,
                "x".repeat(500),
                "",
                null,
                null,
                null);
        assertThat(intento.accion()).hasSize(IntentoDenegado.LARGO_MAXIMO);
        assertThat(intento.protocolo()).isNull();
    }

    @Test
    void elMomentoElCanalYElMotivoSonObligatorios() {
        assertThatNullPointerException()
                .isThrownBy(() -> new IntentoDenegado(
                        null, CanalDeAcceso.API, MotivoDeDenegacion.SIN_CONSENTIMIENTO, null, null, null, null, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new IntentoDenegado(
                        AHORA, null, MotivoDeDenegacion.SIN_CONSENTIMIENTO, null, null, null, null, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new IntentoDenegado(AHORA, CanalDeAcceso.API, null, null, null, null, null, null));
    }
}
