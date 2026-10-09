package tech.tetengo.api.shared.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Detalle;
import tech.tetengo.api.shared.application.port.NotificadorPush.TipoDeAlerta;
import tech.tetengo.api.shared.application.port.TipoAviso;

/** The copy of every notice type, word for word as the prototype (docs/NOTIFICATIONS.md). */
class ContenidoDelAvisoTest {

    private static final UUID ALERTA = UUID.fromString("01928c8e-0000-7000-8000-000000000001");
    private static final UUID CAMARA = UUID.fromString("01928c8e-0000-7000-8000-000000000002");
    /** 10:42 in Lima. */
    private static final Instant A_LAS_10_42 = Instant.parse("2026-09-23T15:42:31Z");
    /** 10:39 in Lima. */
    private static final Instant A_LAS_10_39 = Instant.parse("2026-09-23T15:39:05Z");

    private static final Detalle ROSA = new Detalle("Rosa", null, null, null, null);

    private static ContenidoDelAviso contenido(TipoAviso tipo, Instant cuando, Detalle detalle) {
        return ContenidoDelAviso.de(new Aviso(tipo, ALERTA, CAMARA, "Sala", cuando, detalle));
    }

    private static void esperar(ContenidoDelAviso contenido, String titulo, String cuerpo, String etiqueta) {
        assertThat(contenido.titulo()).isEqualTo(titulo);
        assertThat(contenido.cuerpo()).isEqualTo(cuerpo);
        assertThat(contenido.etiqueta()).isEqualTo(etiqueta);
    }

    @Test
    void pantalla44_caida() {
        esperar(
                contenido(TipoAviso.ALERTA_CAIDA, A_LAS_10_42, ROSA),
                "Posible caída de Rosa en la Sala",
                "10:42 · Toca para ver qué hacer y llamarla.",
                "URGENTE · CAÍDA");
    }

    @Test
    void pantalla49_movimientoInestable() {
        esperar(
                contenido(TipoAviso.ALERTA_MOVIMIENTO_INESTABLE, A_LAS_10_39, ROSA),
                "Rosa tuvo un movimiento inestable en la Sala",
                "10:39 · No es una caída. Revisa cómo está.",
                "SEVERIDAD MEDIA");
    }

    @Test
    void pantalla51_movimientoInestableQueTerminoEnCaida() {
        esperar(
                contenido(
                        TipoAviso.ALERTA_ACTUALIZADA_A_CAIDA,
                        Instant.parse("2026-09-23T15:41:00Z"),
                        new Detalle("Rosa", TipoDeAlerta.CAIDA, A_LAS_10_39, null, null)),
                "Ahora: posible caída de Rosa en la Sala",
                "10:41 · Empezó como movimiento inestable a las 10:39.",
                "URGENTE · CAÍDA");
    }

    @Test
    void prototipo_caidaConfirmada() {
        esperar(
                contenido(TipoAviso.CAIDA_CONFIRMADA, Instant.parse("2026-09-23T15:43:00Z"), ROSA),
                "Rosa sigue en el suelo",
                "Caída confirmada a las 10:43. La alerta sigue activa.",
                null);
    }

    @Test
    void pantalla57_seLevanto() {
        esperar(
                contenido(TipoAviso.SE_LEVANTO, Instant.parse("2026-09-23T15:45:00Z"), ROSA),
                "Rosa se levantó",
                "10:45 · Se puso de pie en la Sala. Confirma cómo está.",
                "SEGUIMIENTO");
    }

    @Test
    void prototipo_otroFamiliarAtendioLaAlerta() {
        esperar(
                contenido(
                        TipoAviso.ALERTA_ATENDIDA,
                        Instant.parse("2026-09-23T15:46:00Z"),
                        new Detalle(null, TipoDeAlerta.CAIDA, null, null, "Carmen")),
                "Carmen atendió la alerta",
                "10:46 · Caída en la Sala.",
                null);
        assertThat(contenido(
                                TipoAviso.ALERTA_ATENDIDA,
                                A_LAS_10_42,
                                new Detalle(null, TipoDeAlerta.MOVIMIENTO_INESTABLE, null, null, "Luis"))
                        .cuerpo())
                .isEqualTo("10:42 · Movimiento inestable en la Sala.");
    }

    @Test
    void prototipo_alertaEscaladaAlContactoSecundario() {
        esperar(
                contenido(TipoAviso.ALERTA_ESCALADA, A_LAS_10_42, new Detalle(null, TipoDeAlerta.CAIDA, null, 5, null)),
                "Nadie atendió la alerta: te toca",
                "Pasaron 5 min sin respuesta. Eres el contacto secundario.",
                "URGENTE · CAÍDA");
        assertThat(contenido(
                                TipoAviso.ALERTA_ESCALADA,
                                A_LAS_10_42,
                                new Detalle(null, TipoDeAlerta.MOVIMIENTO_INESTABLE, null, 3, null))
                        .etiqueta())
                .isEqualTo("SEVERIDAD MEDIA");
    }

    @Test
    void prototipo_sinContactoSecundario() {
        esperar(
                contenido(
                        TipoAviso.SIN_CONTACTO_SECUNDARIO,
                        A_LAS_10_42,
                        new Detalle(null, TipoDeAlerta.CAIDA, null, 10, null)),
                "No hay a quién escalar",
                "Pasaron 10 min y no hay contacto secundario.",
                "URGENTE · CAÍDA");
    }

    @Test
    void pantalla28_camaraDesconectada() {
        esperar(
                contenido(TipoAviso.CAMARA_DESCONECTADA, A_LAS_10_42, Detalle.NINGUNO),
                "La cámara de la Sala se desconectó",
                "Revisa el cable de la cámara, que la PC esté encendida y el internet de la casa.",
                null);
    }

    @Test
    void pantalla29_camaraReconectada() {
        esperar(
                contenido(TipoAviso.CAMARA_RECONECTADA, Instant.parse("2026-09-23T15:52:00Z"), Detalle.NINGUNO),
                "La cámara de la Sala volvió a estar en línea",
                "El monitoreo se restableció a las 10:52.",
                null);
    }

    @Test
    void pantalla36_deteccionNoConfiable() {
        esperar(
                contenido(TipoAviso.DETECCION_NO_CONFIABLE, Instant.parse("2026-09-23T15:41:00Z"), ROSA),
                "La detección no es confiable en la Sala",
                "Hace más de 5 minutos que la cámara no ve bien a Rosa. Revisa la luz y el encuadre.",
                null);
    }

    @Test
    void prototipo_finDeLaPausa() {
        esperar(
                contenido(TipoAviso.PAUSA_FINALIZADA, Instant.parse("2026-09-23T16:42:00Z"), Detalle.NINGUNO),
                "La cámara de la Sala se reactivó",
                "Terminó la pausa a las 11:42.",
                null);
    }

    @Test
    void sinFuente_datosEliminados() {
        var contenido = ContenidoDelAviso.de(new Aviso(TipoAviso.DATOS_ELIMINADOS, null, null, null, A_LAS_10_42));
        esperar(
                contenido,
                "Se eliminaron las grabaciones",
                "Se borraron las grabaciones al revocar el consentimiento.",
                null);
        assertThat(contenido.datos()).containsOnlyKeys("tipo", "ocurridaEn");
    }

    @Test
    void losDatosSonLosDelContratoMasLaEtiqueta() {
        assertThat(contenido(TipoAviso.ALERTA_CAIDA, A_LAS_10_42, ROSA).datos())
                .containsExactly(
                        Map.entry("tipo", "ALERTA_CAIDA"),
                        Map.entry("alertaId", ALERTA.toString()),
                        Map.entry("camaraId", CAMARA.toString()),
                        Map.entry("habitacion", "Sala"),
                        Map.entry("ocurridaEn", "2026-09-23T15:42:31Z"),
                        Map.entry("etiqueta", "URGENTE · CAÍDA"));
        assertThat(contenido(TipoAviso.CAMARA_DESCONECTADA, A_LAS_10_42, ROSA).datos())
                .doesNotContainKey("etiqueta");
    }

    @Test
    void lasHabitacionesLlevanSuArticulo() {
        assertThat(ContenidoDelAviso.de(
                                new Aviso(TipoAviso.CAMARA_DESCONECTADA, null, CAMARA, "Dormitorio", A_LAS_10_42))
                        .titulo())
                .isEqualTo("La cámara del Dormitorio se desconectó");
        assertThat(ContenidoDelAviso.de(new Aviso(TipoAviso.SE_LEVANTO, ALERTA, CAMARA, "Cocina", A_LAS_10_42, ROSA))
                        .cuerpo())
                .isEqualTo("10:42 · Se puso de pie en la Cocina. Confirma cómo está.");
        assertThat(ContenidoDelAviso.de(
                                new Aviso(TipoAviso.PAUSA_FINALIZADA, null, CAMARA, "Cuarto de Rosa", A_LAS_10_42))
                        .titulo())
                .isEqualTo("La cámara de Cuarto de Rosa se reactivó");
    }

    @ParameterizedTest
    @EnumSource(TipoAviso.class)
    void todosLosTiposTienenTituloYCuerpoAunqueFalteAlgunDato(TipoAviso tipo) {
        for (var aviso : List.of(
                new Aviso(tipo, ALERTA, CAMARA, "Sala", A_LAS_10_42, ROSA),
                new Aviso(tipo, ALERTA, CAMARA, "Sala", A_LAS_10_42, Detalle.NINGUNO),
                new Aviso(tipo, null, null, null, null))) {
            var contenido = ContenidoDelAviso.de(aviso);
            assertThat(contenido.titulo()).isNotBlank().doesNotContain("null").doesNotEndWith(" ");
            assertThat(contenido.cuerpo()).isNotBlank().doesNotContain("null");
            assertThat(contenido.datos()).containsEntry("tipo", tipo.name());
        }
    }
}
