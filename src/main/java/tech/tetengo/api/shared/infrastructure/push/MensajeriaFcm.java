package tech.tetengo.api.shared.infrastructure.push;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.time.Duration;
import java.util.List;

/** Seam over {@code FirebaseMessaging.sendEach}, so the FCM adapter can be tested without Firebase. */
interface MensajeriaFcm {

    /** One answer per message, in order. Throws when the whole request fails. */
    List<Respuesta> enviar(List<Message> mensajes) throws FirebaseMessagingException;

    /**
     * @param error null when the message was accepted or the failure has no FCM code
     * @param idDelMensaje FCM's message id ({@code projects/…/messages/…}) when accepted
     * @param reintentarTras the {@code Retry-After} of a failed message, null when FCM sent none
     */
    record Respuesta(boolean aceptada, MessagingErrorCode error, String idDelMensaje, Duration reintentarTras) {

        Respuesta(boolean aceptada, MessagingErrorCode error) {
            this(aceptada, error, null, null);
        }
    }
}
