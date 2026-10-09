package tech.tetengo.api.shared.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Destino;
import tech.tetengo.api.shared.application.port.NotificadorPush.Detalle;
import tech.tetengo.api.shared.application.port.NotificadorPush.FallaDePush;
import tech.tetengo.api.shared.application.port.NotificadorPush.Plataforma;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NotificadorPushSimuladorTest {

    private static final UUID ALERTA = UUID.randomUUID();
    private static final UUID CAMARA = UUID.randomUUID();
    private static final Detalle ROSA = new Detalle("Rosa", null, null, null, null);
    private static final Aviso CAIDA =
            new Aviso(TipoAviso.ALERTA_CAIDA, ALERTA, CAMARA, "Sala", Instant.parse("2026-10-07T15:42:31Z"), ROSA);
    private static final List<Destino> DESTINOS = List.of(new Destino("simulador", Plataforma.IOS));
    private static final PropiedadesDelSimulador PROPIEDADES =
            new PropiedadesDelSimulador("tech.tetengo.teTengo", "booted");

    private final List<List<String>> comandos = new ArrayList<>();
    private final List<String> entradas = new ArrayList<>();

    private NotificadorPushSimulador con(NotificadorPushSimulador.Salida salida) {
        return new NotificadorPushSimulador(PROPIEDADES, (comando, entrada) -> {
            comandos.add(comando);
            entradas.add(entrada);
            return salida;
        });
    }

    @Test
    void laCargaApnsTieneAlertaConSubtituloSonidoGcmMessageIdYLosDatosDelContrato() {
        JsonNode carga = JsonMapper.builder()
                .build()
                .readTree(NotificadorPushSimulador.carga(ContenidoDelAviso.de(CAIDA), "id-1"));

        assertThat(carga.path("aps").path("alert").path("title").asString())
                .isEqualTo("Posible caída de Rosa en la Sala");
        assertThat(carga.path("aps").path("alert").path("subtitle").asString()).isEqualTo("URGENTE · CAÍDA");
        assertThat(carga.path("aps").path("alert").path("body").asString())
                .isEqualTo("10:42 · Toca para ver qué hacer y llamarla.");
        assertThat(carga.path("etiqueta").asString()).isEqualTo("URGENTE · CAÍDA");
        assertThat(carga.path("aps").path("sound").asString()).isEqualTo("default");
        assertThat(carga.path("gcm.message_id").asString()).isEqualTo("id-1");
        assertThat(carga.path("tipo").asString()).isEqualTo("ALERTA_CAIDA");
        assertThat(carga.path("alertaId").asString()).isEqualTo(ALERTA.toString());
        assertThat(carga.path("camaraId").asString()).isEqualTo(CAMARA.toString());
        assertThat(carga.path("habitacion").asString()).isEqualTo("Sala");
        assertThat(carga.path("ocurridaEn").asString()).isEqualTo("2026-10-07T15:42:31Z");
    }

    @Test
    void enviaUnaVezAlSimuladorArrancadoConElBundleDeLaApp() {
        var resultado = con(new NotificadorPushSimulador.Salida(0, "ok"))
                .enviar(List.of(new Destino("a", Plataforma.IOS), new Destino("b", Plataforma.ANDROID)), CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(2);
        assertThat(comandos).containsExactly(List.of("xcrun", "simctl", "push", "booted", "tech.tetengo.teTengo", "-"));
        assertThat(entradas.getFirst()).contains("\"gcm.message_id\"", "\"tipo\":\"ALERTA_CAIDA\"");
    }

    @Test
    void losDispositivosWebNoSeAvisanPorElSimulador() {
        var simulador = con(new NotificadorPushSimulador.Salida(0, "ok"));

        var mixto =
                simulador.enviar(List.of(new Destino("a", Plataforma.IOS), new Destino("pwa", Plataforma.WEB)), CAIDA);
        assertThat(mixto.aceptados()).isEqualTo(1);
        assertThat(comandos).hasSize(1);

        var soloWeb = simulador.enviar(List.of(new Destino("pwa", Plataforma.WEB)), CAIDA);
        assertThat(soloWeb.aceptados()).isZero();
        assertThat(soloWeb.tokensInvalidos()).isEmpty();
        assertThat(comandos).hasSize(1);
    }

    @Test
    void ca16_4_siSimctlFallaSeReintenta() {
        assertThatThrownBy(() -> con(new NotificadorPushSimulador.Salida(149, "No devices are booted."))
                        .enviar(DESTINOS, CAIDA))
                .isInstanceOf(FallaDePush.class)
                .hasMessageContaining("No devices are booted.");
    }

    @Test
    void sinXcrunNoHaceNada() {
        var sinXcrun = new NotificadorPushSimulador(PROPIEDADES, (comando, entrada) -> {
            throw new IOException("Cannot run program xcrun");
        });

        assertThat(sinXcrun.enviar(DESTINOS, CAIDA).aceptados()).isEqualTo(1);
        assertThat(sinXcrun.enviar(DESTINOS, CAIDA).aceptados()).isEqualTo(1);
    }
}
