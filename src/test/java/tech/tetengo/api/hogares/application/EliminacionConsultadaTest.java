package tech.tetengo.api.hogares.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.hogares.application.EliminacionConsultada.Estado;
import tech.tetengo.api.shared.application.port.EliminacionesDeGrabaciones.Eliminacion;

class EliminacionConsultadaTest {

    private static final Instant REVOCADO = Instant.parse("2026-10-10T15:00:00Z");
    private static final Instant TERMINADO = Instant.parse("2026-10-10T15:01:00Z");
    private static final LongSupplier TRES_CLIPS = () -> 3;

    @Test
    void sinRevocacionNiEliminacionNoHayNada() {
        assertThat(EliminacionConsultada.de(Optional.empty(), Optional.empty(), TRES_CLIPS))
                .isEmpty();
    }

    @Test
    void recienRevocadoYAunSinRegistroEstaProgramada() {
        assertThat(EliminacionConsultada.de(Optional.of(REVOCADO), Optional.empty(), TRES_CLIPS))
                .contains(new EliminacionConsultada(Estado.PROGRAMADA, 3, REVOCADO, null));
    }

    @Test
    void pendienteCuentaLosClipsGuardados() {
        var pendiente = new Eliminacion(REVOCADO, null, null);
        assertThat(EliminacionConsultada.de(Optional.of(REVOCADO), Optional.of(pendiente), TRES_CLIPS))
                .contains(new EliminacionConsultada(Estado.PROGRAMADA, 3, REVOCADO, null));
    }

    @Test
    void terminadaDiceCuantosClipsBorroYCuando() {
        var terminada = new Eliminacion(REVOCADO, TERMINADO, 2L);
        assertThat(EliminacionConsultada.de(Optional.of(REVOCADO), Optional.of(terminada), TRES_CLIPS))
                .contains(new EliminacionConsultada(Estado.TERMINADA, 2, REVOCADO, TERMINADO));
    }

    @Test
    void conUnConsentimientoNuevoSigueLaUltimaEliminacion() {
        var terminada = new Eliminacion(REVOCADO, TERMINADO, null);
        assertThat(EliminacionConsultada.de(Optional.empty(), Optional.of(terminada), TRES_CLIPS))
                .contains(new EliminacionConsultada(Estado.TERMINADA, 0, REVOCADO, TERMINADO));
    }

    @Test
    void unaEliminacionDeUnaRevocacionAnteriorNoCuenta() {
        Instant otraRevocacion = TERMINADO.plusSeconds(3600);
        var anterior = new Eliminacion(REVOCADO, TERMINADO, 2L);
        assertThat(EliminacionConsultada.de(Optional.of(otraRevocacion), Optional.of(anterior), () -> 0))
                .contains(new EliminacionConsultada(Estado.PROGRAMADA, 0, otraRevocacion, null));
    }
}
