package tech.tetengo.api.hogares.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** API contract §13: the older adult's photo. */
class HogarFotoTest {

    private final Instant ahora = Instant.parse("2026-10-11T15:00:00Z");
    private final UUID titular = UUID.randomUUID();
    private final Hogar hogar = new Hogar(titular, new AdultoMayor("Rosa Huamán", 78, "Lima", Convivencia.SOLO, null));

    @Test
    void cambiarLaFotoDevuelveLaAnteriorYQuitarlaDosVecesNoFalla() {
        assertThat(hogar.cambiarFotoDelAdultoMayor("fotos/1.jpg", titular, ahora))
                .isEmpty();
        assertThat(hogar.cambiarFotoDelAdultoMayor("fotos/2.jpg", titular, ahora))
                .contains("fotos/1.jpg");
        assertThat(hogar.getFotoDelAdultoMayor()).isEqualTo("fotos/2.jpg");

        assertThat(hogar.quitarFotoDelAdultoMayor(titular, ahora)).contains("fotos/2.jpg");
        assertThat(hogar.quitarFotoDelAdultoMayor(titular, ahora)).isEmpty();
        assertThat(hogar.getFotoDelAdultoMayor()).isNull();
    }

    @Test
    void editarElPerfilNoQuitaLaFoto() {
        hogar.cambiarFotoDelAdultoMayor("fotos/1.jpg", titular, ahora);
        hogar.actualizarAdultoMayor(new AdultoMayor("Rosa", 79, "Lima", Convivencia.SOLO, null));
        assertThat(hogar.getFotoDelAdultoMayor()).isEqualTo("fotos/1.jpg");
    }
}
