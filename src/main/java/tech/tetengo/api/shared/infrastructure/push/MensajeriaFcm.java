package tech.tetengo.api.shared.infrastructure.push;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.List;

/** Seam over {@code FirebaseMessaging.sendEach}, so the FCM adapter can be tested without Firebase. */
interface MensajeriaFcm {

    /** One answer per message, in order. Throws when the whole request fails. */
    List<Respuesta> enviar(List<Message> mensajes) throws FirebaseMessagingException;

    /** {@code error} is null when the message was accepted or the failure has no FCM code. */
    record Respuesta(boolean aceptada, MessagingErrorCode error) {}
}
