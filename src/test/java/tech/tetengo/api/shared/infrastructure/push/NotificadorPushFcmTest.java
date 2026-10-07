package tech.tetengo.api.shared.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Destino;
import tech.tetengo.api.shared.application.port.NotificadorPush.FallaDePush;
import tech.tetengo.api.shared.application.port.NotificadorPush.Plataforma;
import tech.tetengo.api.shared.application.port.NotificadorPush.Resultado;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tools.jackson.databind.json.JsonMapper;

/** The FCM adapter with a fake {@link MensajeriaFcm}: payload, rejected tokens and failures. */
class NotificadorPushFcmTest {

    private static final Aviso CAIDA = new Aviso(
            TipoAviso.ALERTA_CAIDA,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Sala",
            Instant.parse("2026-10-07T15:42:31Z"));

    private static final List<Destino> DESTINOS =
            List.of(new Destino("token-ana", Plataforma.ANDROID), new Destino("token-beto", Plataforma.IOS));

    /** Answers each message with the next queued answer. */
    static class MensajeriaFalsa implements MensajeriaFcm {
        final List<Message> enviados = new ArrayList<>();
        List<Respuesta> respuestas = List.of();
        RuntimeException falla;

        @Override
        public List<Respuesta> enviar(List<Message> mensajes) {
            if (falla != null) {
                throw falla;
            }
            enviados.addAll(mensajes);
            return respuestas;
        }
    }

    private final MensajeriaFalsa mensajeria = new MensajeriaFalsa();
    private final NotificadorPushFcm notificador = new NotificadorPushFcm(mensajeria);

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
        Map<String, Object> esperado = new LinkedHashMap<>(CargasPush.mensajeFcm(ContenidoDelAviso.de(CAIDA)));
        esperado.put("token", "token-ana");
        assertThat(json(mensajeria.enviados.getFirst())).isEqualTo(normalizar(esperado));
    }

    @Test
    void losTokensQueFcmYaNoAceptaSeReportan() {
        mensajeria.respuestas = List.of(aceptada(), error(MessagingErrorCode.UNREGISTERED));

        Resultado resultado = notificador.enviar(DESTINOS, CAIDA);

        assertThat(resultado.aceptados()).isEqualTo(1);
        assertThat(resultado.tokensInvalidos()).containsExactly("token-beto");
    }

    @Test
    void siTodosLosTokensSonInvalidosNoEsUnaFalla() {
        mensajeria.respuestas =
                List.of(error(MessagingErrorCode.INVALID_ARGUMENT), error(MessagingErrorCode.SENDER_ID_MISMATCH));

        Resultado resultado = notificador.enviar(DESTINOS, CAIDA);

        assertThat(resultado.aceptados()).isZero();
        assertThat(resultado.tokensInvalidos()).containsExactlyInAnyOrder("token-ana", "token-beto");
    }

    @Test
    void ca16_4_siFcmNoAceptaNingunoPorOtraRazonSeReintenta() {
        mensajeria.respuestas = List.of(error(MessagingErrorCode.UNAVAILABLE), error(MessagingErrorCode.UNREGISTERED));
        assertThatThrownBy(() -> notificador.enviar(DESTINOS, CAIDA)).isInstanceOf(FallaDePush.class);
    }

    @Test
    void unaFallaParcialNoReintentaAQuienesYaLoRecibieron() {
        mensajeria.respuestas = List.of(aceptada(), error(MessagingErrorCode.INTERNAL));
        assertThat(notificador.enviar(DESTINOS, CAIDA).aceptados()).isEqualTo(1);
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
