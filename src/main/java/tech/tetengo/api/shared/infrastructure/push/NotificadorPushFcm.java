package tech.tetengo.api.shared.infrastructure.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tetengo.api.shared.application.port.NotificadorPush;

/**
 * Push through Firebase Cloud Messaging (HTTP v1, Firebase Admin SDK) to the FCM registration tokens
 * the app registers on Android, iOS and the web (PWA): every message carries the Android, APNs and
 * web push blocks, and FCM applies the one of the token's platform. Tokens FCM no longer accepts ({@code UNREGISTERED},
 * {@code SENDER_ID_MISMATCH}, {@code INVALID_ARGUMENT}) are reported so their devices are
 * deactivated. When FCM accepts no device for any other reason, it throws {@link FallaDePush} so the
 * notice is retried (CA-16.4).
 */
class NotificadorPushFcm implements NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPushFcm.class);

    private static final Set<MessagingErrorCode> TOKEN_INVALIDO = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.INVALID_ARGUMENT);

    private final MensajeriaFcm mensajeria;
    private final String enlaceWeb;

    /**
     * @param enlaceWeb the PWA's URL that web notifications open ({@link PropiedadesDePushWeb}); null
     *     sends none
     */
    NotificadorPushFcm(MensajeriaFcm mensajeria, String enlaceWeb) {
        this.mensajeria = mensajeria;
        this.enlaceWeb = enlaceWeb;
    }

    @Override
    public Resultado enviar(List<Destino> destinos, Aviso aviso) {
        ContenidoDelAviso contenido = ContenidoDelAviso.de(aviso);
        List<Message> mensajes = destinos.stream()
                .map(d -> mensaje(d.tokenPush(), contenido, enlaceWeb))
                .toList();
        List<MensajeriaFcm.Respuesta> respuestas;
        try {
            respuestas = mensajeria.enviar(mensajes);
        } catch (FirebaseMessagingException | RuntimeException e) {
            throw new FallaDePush("FCM no respondió al push " + aviso.tipo(), e);
        }
        int aceptados = 0;
        Set<String> invalidos = new HashSet<>();
        Set<MessagingErrorCode> errores = new HashSet<>();
        for (int i = 0; i < respuestas.size(); i++) {
            MensajeriaFcm.Respuesta respuesta = respuestas.get(i);
            if (respuesta.aceptada()) {
                aceptados++;
            } else if (TOKEN_INVALIDO.contains(respuesta.error())) {
                invalidos.add(destinos.get(i).tokenPush());
            } else {
                errores.add(respuesta.error());
            }
        }
        if (aceptados == 0 && !errores.isEmpty()) {
            throw new FallaDePush("FCM no aceptó el push " + aviso.tipo() + ": " + errores, null);
        }
        if (!errores.isEmpty()) {
            log.warn("FCM no aceptó el push {} para algunos dispositivos: {}", aviso.tipo(), errores);
        }
        return new Resultado(aceptados, invalidos, Map.of());
    }

    /** The same payload as {@link CargasPush#mensajeFcm}, built with the Admin SDK. */
    static Message mensaje(String token, ContenidoDelAviso contenido, String enlaceWeb) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(contenido.titulo())
                        .setBody(contenido.cuerpo())
                        .build())
                .putAllData(contenido.datos())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder()
                                .setAlert(alertaApns(contenido))
                                .setSound("default")
                                .build())
                        .build())
                .setWebpushConfig(webpush(contenido, enlaceWeb))
                .build();
    }

    private static WebpushConfig webpush(ContenidoDelAviso contenido, String enlaceWeb) {
        WebpushConfig.Builder webpush = WebpushConfig.builder()
                .putHeader(CargasPush.URGENCIA, CargasPush.URGENCIA_ALTA)
                .setNotification(WebpushNotification.builder()
                        .setTitle(contenido.titulo())
                        .setBody(contenido.cuerpo())
                        .build());
        if (enlaceWeb != null) {
            webpush.setFcmOptions(WebpushFcmOptions.withLink(enlaceWeb));
        }
        return webpush.build();
    }

    private static ApsAlert alertaApns(ContenidoDelAviso contenido) {
        ApsAlert.Builder alerta = ApsAlert.builder().setTitle(contenido.titulo());
        if (contenido.etiqueta() != null) {
            alerta.setSubtitle(contenido.etiqueta());
        }
        return alerta.setBody(contenido.cuerpo()).build();
    }
}
