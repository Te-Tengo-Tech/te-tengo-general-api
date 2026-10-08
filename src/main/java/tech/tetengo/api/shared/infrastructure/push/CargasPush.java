package tech.tetengo.api.shared.infrastructure.push;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire payloads of a notice, the same for every provider: the FCM HTTP v1 message (FCM, and SNS
 * {@code GCM} endpoints) and the APNs payload (SNS {@code APNS} endpoints and the iOS simulator). The
 * label goes as the iOS subtitle and as {@code etiqueta} in the data, since Android has no subtitle.
 * Pushes go with high priority and the default sound: a fall must arrive in less than 10 s (CA-16.1).
 * The FCM message carries the Android, APNs and web push blocks; FCM applies the one of the token's
 * platform, so the same message serves the native app and the PWA.
 */
final class CargasPush {

    private CargasPush() {}

    /** Web Push header that asks the browser's push service to deliver at once (RFC 8030). */
    static final String URGENCIA = "Urgency";

    static final String URGENCIA_ALTA = "high";

    /**
     * The {@code message} of FCM HTTP v1 without its {@code token}.
     *
     * @param enlaceWeb the PWA's URL the web notification opens; null sends none
     */
    static Map<String, Object> mensajeFcm(ContenidoDelAviso contenido, String enlaceWeb) {
        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("notification", Map.of("title", contenido.titulo(), "body", contenido.cuerpo()));
        mensaje.put("data", contenido.datos());
        mensaje.put("android", Map.of("priority", "high", "notification", Map.of("sound", "default")));
        mensaje.put("apns", Map.of("headers", Map.of("apns-priority", "10"), "payload", Map.of("aps", aps(contenido))));
        mensaje.put("webpush", webpush(contenido, enlaceWeb));
        return mensaje;
    }

    /**
     * FCM's {@code webpush} block: high urgency, the notification the PWA's service worker shows and,
     * when configured, the link it opens on click ({@code fcm_options.link}, HTTPS only).
     */
    private static Map<String, Object> webpush(ContenidoDelAviso contenido, String enlaceWeb) {
        Map<String, Object> webpush = new LinkedHashMap<>();
        webpush.put("headers", Map.of(URGENCIA, URGENCIA_ALTA));
        webpush.put("notification", Map.of("title", contenido.titulo(), "body", contenido.cuerpo()));
        if (enlaceWeb != null) {
            webpush.put("fcm_options", Map.of("link", enlaceWeb));
        }
        return webpush;
    }

    /**
     * APNs payload: {@code aps} with the alert and sound, plus the data keys at the top level, where
     * FlutterFire reads them. {@code gcm.message_id} makes FlutterFire treat it as an FCM message,
     * otherwise the app ignores it.
     */
    static Map<String, Object> apns(ContenidoDelAviso contenido, String idDelMensaje) {
        Map<String, Object> carga = new LinkedHashMap<>();
        carga.put("aps", aps(contenido));
        carga.put("gcm.message_id", idDelMensaje);
        carga.putAll(contenido.datos());
        return carga;
    }

    /** The notice's label, when it has one, is the iOS subtitle ({@code aps.alert.subtitle}). */
    private static Map<String, Object> aps(ContenidoDelAviso contenido) {
        Map<String, Object> alerta = new LinkedHashMap<>();
        alerta.put("title", contenido.titulo());
        if (contenido.etiqueta() != null) {
            alerta.put("subtitle", contenido.etiqueta());
        }
        alerta.put("body", contenido.cuerpo());
        return Map.of("alert", alerta, "sound", "default");
    }
}
