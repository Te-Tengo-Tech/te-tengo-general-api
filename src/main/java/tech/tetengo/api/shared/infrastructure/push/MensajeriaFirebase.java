package tech.tetengo.api.shared.infrastructure.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.SendResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** {@link MensajeriaFcm} with the Firebase Admin SDK (FCM HTTP v1). */
final class MensajeriaFirebase implements MensajeriaFcm {

    /** FCM accepts at most 500 messages per {@code sendEach}. */
    private static final int LOTE = 500;

    private static final String APP = "te-tengo";

    private final FirebaseMessaging mensajeria;

    MensajeriaFirebase(FirebaseMessaging mensajeria) {
        this.mensajeria = mensajeria;
    }

    /** Reads the service-account key; the API does not start without it. */
    static MensajeriaFirebase crear(PropiedadesDeFcm propiedades) {
        if (!StringUtils.hasText(propiedades.credenciales())) {
            throw new IllegalStateException(
                    "tetengo.push.fcm.credenciales (TT_FCM_CREDENCIALES) es obligatorio con FCM");
        }
        GoogleCredentials credenciales;
        try (InputStream clave = Files.newInputStream(Path.of(propiedades.credenciales()))) {
            credenciales = GoogleCredentials.fromStream(clave);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la clave de Firebase " + propiedades.credenciales(), e);
        }
        return new MensajeriaFirebase(FirebaseMessaging.getInstance(app(credenciales)));
    }

    static FirebaseApp app(GoogleCredentials credenciales) {
        return FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(APP))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(
                        FirebaseOptions.builder().setCredentials(credenciales).build(), APP));
    }

    @Override
    public List<Respuesta> enviar(List<Message> mensajes) throws FirebaseMessagingException {
        List<Respuesta> respuestas = new ArrayList<>(mensajes.size());
        for (int i = 0; i < mensajes.size(); i += LOTE) {
            for (SendResponse r : mensajeria
                    .sendEach(mensajes.subList(i, Math.min(i + LOTE, mensajes.size())))
                    .getResponses()) {
                respuestas.add(
                        r.isSuccessful()
                                ? new Respuesta(true, null)
                                : new Respuesta(false, r.getException().getMessagingErrorCode()));
            }
        }
        return respuestas;
    }
}
