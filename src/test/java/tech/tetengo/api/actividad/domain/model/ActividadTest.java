package tech.tetengo.api.actividad.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.actividad.application.RegistroDeActividad;

class ActividadTest {

    @Test
    void losTextosSeRecortanAUnaLineaYAlLargoDeLaColumna() {
        Actividad actividad = Actividad.de(
                        "c", Instant.EPOCH, TipoDeActividad.GUARDADO_LOCAL_CAMBIADO, OrigenDeActividad.PC)
                .enPc(UUID.randomUUID(), "Sala-Rosa\n")
                .sobre("Video\tcontinuo", "  ", "x".repeat(300));
        assertThat(actividad.getPcNombre()).isEqualTo("Sala-Rosa");
        assertThat(actividad.getObjeto()).isEqualTo("Video continuo");
        assertThat(actividad.getValorAnterior()).isNull();
        assertThat(actividad.getValorNuevo()).hasSize(200);
    }

    @Test
    void soloLosCambiosLocalesLosInformaLaPc() {
        assertThat(TipoDeActividad.MODO_AVANZADO_ACTIVADO.reportadoPorLaPc()).isTrue();
        assertThat(TipoDeActividad.DATOS_LOCALES_ELIMINADOS.reportadoPorLaPc()).isTrue();
        assertThat(TipoDeActividad.CAMARA_ASIGNADA.reportadoPorLaPc()).isFalse();
        assertThat(TipoDeActividad.PC_VINCULADA.reportadoPorLaPc()).isFalse();
    }

    @Test
    void laClaveEsEstableYCorta() {
        UUID pc = UUID.randomUUID();
        assertThat(RegistroDeActividad.clave("PC", pc, 1)).isEqualTo(RegistroDeActividad.clave("PC", pc, 1));
        assertThat(RegistroDeActividad.clave("PC", pc, 1)).isNotEqualTo(RegistroDeActividad.clave("PC", pc, 2));
        assertThat(RegistroDeActividad.clave("PC", pc, 1)).hasSize(36);
    }
}
