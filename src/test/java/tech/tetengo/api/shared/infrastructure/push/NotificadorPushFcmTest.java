package tech.tetengo.api.shared.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Destino;
import tech.tetengo.api.shared.application.port.NotificadorPush.Detalle;
import tech.tetengo.api.shared.application.port.NotificadorPush.FallaDePush;
import tech.tetengo.api.shared.application.port.NotificadorPush.Plataforma;
import tech.tetengo.api.shared.application.port.NotificadorPush.Resultado;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tools.jackson.databind.json.JsonMapper;

/** The FCM adapter with a fake {@link MensajeriaFcm}: payload, rejected tokens, retries and failures. */
class NotificadorPushFcmTest {

    private static final Aviso CAIDA = new Aviso(
            TipoAviso.ALERTA_CAIDA,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Sala",
            Instant.parse("2026-10-07T15:42:31Z"),
            new Detalle("Rosa", null, null, null, null));

    private static final List<Destino> DESTINOS =
            List.of(new Destino("token-ana", Plataforma.ANDROID), new Destino("token-beto", Plataforma.IOS));

    private static final String PWA = "https://te-tengo.pages.dev/app/";

    private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    /**
     * Answers each send with the next queued round ({@link #rondas}), or else with {@link #respuestas}.
     */
    static class MensajeriaFalsa implements MensajeriaFcm {
        final List<Message> enviados = new ArrayList<>();
        final List<Integer> lotes = new ArrayList<>();
        final Deque<List<Respuesta>> rondas = new ArrayDeque<>();
        List<Respuesta> respuestas = List.of();
        RuntimeException falla;

        @Override
        public List<Respuesta> enviar(List<Message> mensajes) {
            if (falla != null) {
                throw falla;
            }
            enviados.addAll(mensajes);
            lotes.add(mensajes.size());
            return rondas.isEmpty() ? respuestas : rondas.poll();
        }
    }

    private final MensajeriaFalsa mensajeria = new MensajeriaFalsa();
    private final List<Duration> esperas = new ArrayList<>();
    private final NotificadorPushFcm notificador = new NotificadorPushFcm(mensajeria, PWA, RELOJ, esperas::add);

    private static MensajeriaFcm.Respuesta aceptada() {
        return new MensajeriaFcm.Respuesta(true, null);
    }

    private static MensajeriaFcm.Respuesta error(MessagingErrorCode codigo) {
        return new MensajeriaFcm.Respuesta(false, codigo);
    }

    @Test
    void unMensajePorDispositivoConElMismoContenidoQueLasDemasCargas() {
        mensajeria.respuestas = List.of(aceptada(), aceptada());

        Resultado resultado = notificador.enviar(DESTINOS, CAIDA);

        assertThat(resultado).isEqualTo(new Resultado(2, java.util.Set.of(), Map.of()));
        assertThat(mensajeria.enviados).hasSize(2);
        Map<String, Object> esperado =
                new LinkedHashMap<>(CargasPush.mensajeFcm(ContenidoDelAviso.de(CAIDA), PWA, AHORA));
        esperado.put("token", "token-ana");
        assertThat(json(mensajeria.enviados.getFirst())).isEqualTo(normalizar(esperado));
    }

    @Test
    void laEtiquetaVaComoSubtituloEnIosYEnLosDatos() {
        mensajeria.respuestas = List.of(aceptada(), aceptada());

        notificador.enviar(DESTINOS, CAIDA);

        @SuppressWarnings("unchecked")
        Map<String, Object> mensaje = (Map<String, Object>) json(mensajeria.enviados.getFirst());
        assertThat(mensaje)
                .extractingByKey("notification")
                .isEqualTo(Map.of(
                        "title",
                        "Posible caída de Rosa en la Sala",
                        "body",
                        "10:42 · Toca para ver qué hacer y llamarla."));
        assertThat(mensaje)
                .extractingByKey("data")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("etiqueta", "URGENTE · CAÍDA");
        assertThat(mensaje)
                .extractingByKey("apns")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .extractingByKey("payload")
                .isEqualTo(Map.of(
                        "aps",
                        Map.of(
                                "alert",
                                Map.of(
                                        "title", "Posible caída de Rosa en la Sala",
                                        "subtitle", "URGENTE · CAÍDA",
                                        "body", "10:42 · Toca para ver qué hacer y llamarla."),
                                "sound",
                                "default",
                                "thread-id",
                                CAIDA.alertaId().toString(),
                                "interruption-level",
                                "time-sensitive")));
    }

    @Test
    void laPwaRecibeElBloqueWebpushConElEnlaceALaApp() {
        mensajeria.respuestas = List.of(aceptada());

        Resultado resultado = notificador.enviar(List.of(new Destino("token-web", Plataforma.WEB)), CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> mensaje = (Map<String, Object>) json(mensajeria.enviados.getFirst());
        assertThat(mensaje).containsEntry("token", "token-web");
        assertThat(mensaje)
                .extractingByKey("webpush")
                .isEqualTo(Map.of(
                        "headers",
                        Map.of("Urgency", "high", "TTL", "3600"),
                        "notification",
                        Map.of(
                                "title",
                                "Posible caída de Rosa en la Sala",
                                "body",
                                "10:42 · Toca para ver qué hacer y llamarla.",
                                "tag",
                                CAIDA.alertaId().toString(),
                                "requireInteraction",
                                true),
                        "fcm_options",
                        Map.of("link", PWA + "#/alerta/" + CAIDA.alertaId())));
        // The contract's data payload reaches the PWA too.
        assertThat(mensaje)
                .extractingByKey("data")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("tipo", "ALERTA_CAIDA")
                .containsEntry("habitacion", "Sala");
    }

    @Test
    void sinUrlDeLaPwaElAvisoWebNoLlevaEnlace() {
        mensajeria.respuestas = List.of(aceptada());

        new NotificadorPushFcm(mensajeria, null, RELOJ, esperas::add)
                .enviar(List.of(new Destino("token-web", Plataforma.WEB)), CAIDA);

        @SuppressWarnings("unchecked")
        Map<String, Object> mensaje = (Map<String, Object>) json(mensajeria.enviados.getFirst());
        assertThat(mensaje)
                .extractingByKey("webpush")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKey("fcm_options")
                .containsKey("notification");
        assertThat(CargasPush.mensajeFcm(ContenidoDelAviso.de(CAIDA), null, AHORA))
                .extractingByKey("webpush")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKey("fcm_options");
    }

    @Test
    void laUrlDeLaPwaDebeSerHttps() {
        assertThat(new PropiedadesDePushWeb("  ").enlace()).isNull();
        assertThat(new PropiedadesDePushWeb(PWA).enlace()).isEqualTo(PWA);
        assertThatThrownBy(() -> new PropiedadesDePushWeb("http://localhost:5000/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TT_PWA_URL");
    }

    @Test
    void losTokensQueFcmYaNoAceptaSeReportan() {
        mensajeria.respuestas = List.of(aceptada(), error(MessagingErrorCode.UNREGISTERED));

        Resultado resultado = notificador.enviar(DESTINOS, CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(1);
        assertThat(resultado.tokensInvalidos()).containsExactly("token-beto");
    }

    @Test
    void siTodosLosTokensYaNoExistenNoEsUnaFalla() {
        mensajeria.respuestas =
                List.of(error(MessagingErrorCode.UNREGISTERED), error(MessagingErrorCode.SENDER_ID_MISMATCH));

        Resultado resultado = notificador.enviar(DESTINOS, CAIDA);

        assertThat(resultado.aceptados()).isZero();
        assertThat(resultado.tokensInvalidos()).containsExactlyInAnyOrder("token-ana", "token-beto");
    }

    @Test
    void invalidArgumentNoDesactivaElDispositivo() {
        // FCM also answers INVALID_ARGUMENT for a payload it rejects: deactivating would silence the phone.
        mensajeria.respuestas = List.of(aceptada(), error(MessagingErrorCode.INVALID_ARGUMENT));

        Resultado resultado = notificador.enviar(DESTINOS, CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(1);
        assertThat(resultado.tokensInvalidos()).isEmpty();
        assertThat(mensajeria.lotes).containsExactly(2);
    }

    @Test
    void ca16_4_siTodosDanInvalidArgumentEsUnaFallaQueSeReintentaMasTarde() {
        mensajeria.respuestas =
                List.of(error(MessagingErrorCode.INVALID_ARGUMENT), error(MessagingErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> notificador.enviar(DESTINOS, CAIDA)).isInstanceOf(FallaDePush.class);
    }

    @Test
    void ca16_4_siFcmNoAceptaNingunoPorOtraRazonSeReintenta() {
        mensajeria.respuestas = List.of(error(MessagingErrorCode.UNAVAILABLE), error(MessagingErrorCode.UNREGISTERED));
        assertThatThrownBy(() -> notificador.enviar(DESTINOS, CAIDA)).isInstanceOf(FallaDePush.class);
        // The transient one was retried before giving up; the gone token was not.
        assertThat(mensajeria.lotes).containsExactly(2, 1, 1);
        assertThat(esperas).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void unErrorTransitorioSeReintentaSoloParaElDispositivoQueFallo() {
        mensajeria.rondas.add(List.of(aceptada(), error(MessagingErrorCode.UNAVAILABLE)));
        mensajeria.rondas.add(List.of(aceptada()));

        Resultado resultado = notificador.enviar(DESTINOS, CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(2);
        assertThat(mensajeria.lotes).containsExactly(2, 1);
        assertThat(json(mensajeria.enviados.get(2)).toString()).contains("token-beto");
    }

    @Test
    void seRespetaElRetryAfterDeFcmHastaUnMaximo() {
        mensajeria.rondas.add(List.of(
                new MensajeriaFcm.Respuesta(false, MessagingErrorCode.QUOTA_EXCEEDED, null, Duration.ofSeconds(4))));
        mensajeria.rondas.add(
                List.of(new MensajeriaFcm.Respuesta(false, MessagingErrorCode.INTERNAL, null, Duration.ofSeconds(90))));
        mensajeria.rondas.add(List.of(aceptada()));

        Resultado resultado = notificador.enviar(List.of(DESTINOS.getFirst()), CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(1);
        assertThat(esperas).containsExactly(Duration.ofSeconds(4), NotificadorPushFcm.ESPERA_MAXIMA);
    }

    @Test
    void unaFallaParcialNoReintentaAQuienesYaLoRecibieron() {
        mensajeria.rondas.add(List.of(aceptada(), error(MessagingErrorCode.INTERNAL)));
        mensajeria.respuestas = List.of(error(MessagingErrorCode.INTERNAL));
        assertThat(notificador.enviar(DESTINOS, CAIDA).aceptados()).isEqualTo(1);
        assertThat(mensajeria.lotes).containsExactly(2, 1, 1);
    }

    @Test
    void laCaidaEsUrgenteEnAndroidIosYLaWeb() {
        mensajeria.respuestas = List.of(aceptada(), aceptada());

        notificador.enviar(DESTINOS, CAIDA);

        @SuppressWarnings("unchecked")
        Map<String, Object> mensaje = (Map<String, Object>) json(mensajeria.enviados.getFirst());
        String alerta = CAIDA.alertaId().toString();
        assertThat(mensaje)
                .extractingByKey("android")
                .isEqualTo(Map.of(
                        "priority",
                        "high",
                        "ttl",
                        "3600s",
                        "notification",
                        Map.of("sound", "default", "tag", alerta, "channel_id", "alertas_caida")));
        assertThat(mensaje)
                .extractingByKey("apns")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .extractingByKey("headers")
                .isEqualTo(Map.of(
                        "apns-priority",
                        "10",
                        "apns-push-type",
                        "alert",
                        "apns-expiration",
                        String.valueOf(AHORA.plusSeconds(3600).getEpochSecond()),
                        "apns-collapse-id",
                        alerta));
    }

    @Test
    void unAvisoDeCamaraNoEsUrgenteNiVaAlCanalDeAlertas() {
        Aviso desconectada = new Aviso(
                TipoAviso.CAMARA_DESCONECTADA,
                null,
                UUID.fromString("0199c0de-0000-7000-8000-000000000001"),
                "Sala",
                AHORA);
        mensajeria.respuestas = List.of(aceptada());

        notificador.enviar(List.of(new Destino("token-web", Plataforma.WEB)), desconectada);

        @SuppressWarnings("unchecked")
        Map<String, Object> mensaje = (Map<String, Object>) json(mensajeria.enviados.getFirst());
        assertThat(mensaje.toString())
                .doesNotContain("alertas_caida")
                .doesNotContain("time-sensitive")
                .doesNotContain("requireInteraction")
                .contains("camara-0199c0de-0000-7000-8000-000000000001");
        assertThat(mensaje)
                .extractingByKey("webpush")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .extractingByKey("fcm_options")
                .isEqualTo(Map.of("link", PWA));
    }

    @Test
    void elRetryAfterSeLeeEnSegundosOComoFecha() {
        assertThat(MensajeriaFirebase.duracion("7", AHORA)).isEqualTo(Duration.ofSeconds(7));
        assertThat(MensajeriaFirebase.duracion("Sat, 10 Oct 2026 15:00:30 GMT", AHORA))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(MensajeriaFirebase.duracion("pronto", AHORA)).isNull();
    }

    @Test
    void laHuellaDelTokenNoLoRevela() {
        assertThat(HuellaDeToken.de("token-ana")).hasSize(12).doesNotContain("token");
        assertThat(HuellaDeToken.de("token-ana")).isEqualTo(HuellaDeToken.de("token-ana"));
    }

    @Test
    void ca16_4_siFcmNoRespondeSeReintenta() {
        mensajeria.falla = new IllegalStateException("sin red");
        assertThatThrownBy(() -> notificador.enviar(DESTINOS, CAIDA))
                .isInstanceOf(FallaDePush.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void elSdkSeIniciaSinFirestoreNiCloudStorage() {
        var credenciales = GoogleCredentials.create(new AccessToken("prueba", null));
        // projectId is required by FCM HTTP v1; a real key carries it.
        var app = com.google.firebase.FirebaseApp.initializeApp(
                com.google.firebase.FirebaseOptions.builder()
                        .setCredentials(credenciales)
                        .setProjectId("te-tengo-prueba")
                        .build(),
                "prueba-" + UUID.randomUUID());
        try {
            assertThat(new MensajeriaFirebase(FirebaseMessaging.getInstance(app)))
                    .isNotNull();
        } finally {
            app.delete();
        }
    }

    private static Object json(Message mensaje) {
        try {
            return JsonMapper.builder()
                    .build()
                    .readValue(GsonFactory.getDefaultInstance().toString(mensaje), Map.class);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Object normalizar(Map<String, Object> mapa) {
        var json = JsonMapper.builder().build();
        return json.readValue(json.writeValueAsString(mapa), Map.class);
    }
}
