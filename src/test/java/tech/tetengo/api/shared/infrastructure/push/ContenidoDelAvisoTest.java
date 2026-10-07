package tech.tetengo.api.shared.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;

class ContenidoDelAvisoTest {

    private static final UUID ALERTA = UUID.fromString("01928c8e-0000-7000-8000-000000000001");
    private static final UUID CAMARA = UUID.fromString("01928c8e-0000-7000-8000-000000000002");
    /** 10:42 in Lima. */
    private static final Instant CUANDO = Instant.parse("2026-10-07T15:42:31Z");

    @Test
    void laCaidaLlevaHabitacionYHoraDelHogarYLosDatosDelContrato() {
        var contenido = ContenidoDelAviso.de(new Aviso(TipoAviso.ALERTA_CAIDA, ALERTA, CAMARA, "Sala", CUANDO));

        assertThat(contenido.titulo()).isEqualTo("Posible caída en la Sala");
        assertThat(contenido.cuerpo()).isEqualTo("10:42 · Toca para ver qué hacer.");
        assertThat(contenido.datos())
                .containsExactly(
                        java.util.Map.entry("tipo", "ALERTA_CAIDA"),
                        java.util.Map.entry("alertaId", ALERTA.toString()),
                        java.util.Map.entry("camaraId", CAMARA.toString()),
                        java.util.Map.entry("habitacion", "Sala"),
                        java.util.Map.entry("ocurridaEn", "2026-10-07T15:42:31Z"));
    }

    @Test
    void losDatosAusentesNoSeEnvian() {
        var contenido = ContenidoDelAviso.de(new Aviso(TipoAviso.DATOS_ELIMINADOS, null, null, null, CUANDO));
        assertThat(contenido.datos()).containsOnlyKeys("tipo", "ocurridaEn");
        assertThat(contenido.titulo()).isEqualTo("Se eliminaron las grabaciones");
    }

    @Test
    void lasHabitacionesConocidasLlevanSuArticulo() {
        assertThat(ContenidoDelAviso.de(new Aviso(TipoAviso.CAMARA_DESCONECTADA, null, CAMARA, "Dormitorio", CUANDO))
                        .titulo())
                .isEqualTo("La cámara del Dormitorio se desconectó");
        assertThat(ContenidoDelAviso.de(new Aviso(TipoAviso.PAUSA_FINALIZADA, null, CAMARA, "Cuarto de Rosa", CUANDO)))
                .extracting(ContenidoDelAviso::titulo, ContenidoDelAviso::cuerpo)
                .containsExactly("La cámara de Cuarto de Rosa se reactivó", "Terminó la pausa a las 10:42.");
        assertThat(ContenidoDelAviso.de(new Aviso(TipoAviso.SE_LEVANTO, ALERTA, CAMARA, "Cocina", CUANDO))
                        .cuerpo())
                .isEqualTo("10:42 · Se puso de pie en la Cocina. Confirma cómo está.");
    }

    @ParameterizedTest
    @EnumSource(TipoAviso.class)
    void todosLosTiposTienenTituloYCuerpoConOSinHabitacionNiHora(TipoAviso tipo) {
        for (var aviso : java.util.List.of(
                new Aviso(tipo, ALERTA, CAMARA, "Sala", CUANDO), new Aviso(tipo, null, null, null, null))) {
            var contenido = ContenidoDelAviso.de(aviso);
            assertThat(contenido.titulo()).isNotBlank().doesNotContain("null").doesNotEndWith(" ");
            assertThat(contenido.cuerpo()).isNotBlank().doesNotContain("null");
            assertThat(contenido.datos()).containsEntry("tipo", tipo.name());
        }
    }
}
