package tech.tetengo.api.shared.infrastructure.push;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire payloads of a notice, the same for every provider: the FCM HTTP v1 message (FCM, and SNS
 * {@code GCM} endpoints) and the APNs payload (SNS {@code APNS} endpoints and the iOS simulator). The
 * label goes as the iOS subtitle and as {@code etiqueta} in the data, since Android has no subtitle.
 *
 * <p>Every notice goes with high priority and the default sound: a fall must arrive in less than 10 s
 * (CA-16.1). On top of that (docs/NOTIFICATIONS.md, «Urgency»):
 *
 * <ul>
 *   <li><b>Lifetime</b> {@link #VIGENCIA}: Android {@code ttl}, APNs {@code apns-expiration}, web push
 *       {@code TTL}. A phone that comes back online later gets nothing stale; the alert is in the
 *       app anyway.
 *   <li><b>Grouping</b> ({@link ContenidoDelAviso#grupo()}): Android {@code tag}, APNs
 *       {@code apns-collapse-id}, web {@code tag}: a later notice of the same alert replaces the
 *       earlier one on screen.
 *   <li><b>Notices about an alert</b> go to the app's Android channel {@link #CANAL_ALERTAS}, which
 *       the app creates with high importance; other notices use the app's default channel.
 *   <li><b>Urgent notices</b> ({@code TipoAviso.urgente()}): iOS {@code interruption-level:
 *       time-sensitive} (it breaks through Focus when the app has the entitlement) and a web
 *       notification that stays until dismissed ({@code requireInteraction}). No {@code renotify}:
 *       the PWA's service worker shows every push itself and Firebase's own notification then
 *       replaces it by {@code tag}, which must not alert twice.
 * </ul>
 *
 * The FCM message carries the Android, APNs and web push blocks; FCM applies the one of the token's
 * platform, so the same message serves the native app and the PWA.
 */
final class CargasPush {

    private CargasPush() {}

    /** Web Push header that asks the browser's push service to deliver at once (RFC 8030). */
    static final String URGENCIA = "Urgency";

    static final String URGENCIA_ALTA = "high";

    /** Android notification channel of alert notices, created by the app with high importance. */
    static final String CANAL_ALERTAS = "alertas_caida";

    /** How long the push services keep an undelivered notice [implementation choice]. */
    static final Duration VIGENCIA = Duration.ofHours(1);

    /**
     * The {@code message} of FCM HTTP v1 without its {@code token}.
     *
     * @param enlaceWeb the PWA's URL the web notification opens; null sends none
     * @param ahora when it is sent, for the APNs expiration
     */
    static Map<String, Object> mensajeFcm(ContenidoDelAviso contenido, String enlaceWeb, Instant ahora) {
        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("notification", Map.of("title", contenido.titulo(), "body", contenido.cuerpo()));
        mensaje.put("data", contenido.datos());
        mensaje.put("android", android(contenido));
        mensaje.put(
                "apns", Map.of("headers", cabecerasApns(contenido, ahora), "payload", Map.of("aps", aps(contenido))));
        mensaje.put("webpush", webpush(contenido, enlaceWeb));
        return mensaje;
    }

    private static Map<String, Object> android(ContenidoDelAviso contenido) {
        Map<String, Object> notificacion = new LinkedHashMap<>();
        notificacion.put("sound", "default");
        notificacion.put("tag", contenido.grupo());
        if (contenido.alertaId() != null) {
            notificacion.put("channel_id", CANAL_ALERTAS);
        }
        Map<String, Object> android = new LinkedHashMap<>();
        android.put("priority", "high");
        android.put("ttl", VIGENCIA.toSeconds() + "s");
        android.put("notification", notificacion);
        return android;
    }

    /** APNs headers: immediate delivery, an alert push, its expiration and its collapse id. */
    static Map<String, String> cabecerasApns(ContenidoDelAviso contenido, Instant ahora) {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put("apns-priority", "10");
        cabeceras.put("apns-push-type", "alert");
        cabeceras.put("apns-expiration", String.valueOf(ahora.plus(VIGENCIA).getEpochSecond()));
        cabeceras.put("apns-collapse-id", contenido.grupo());
        return cabeceras;
    }

    /** Web push headers: high urgency and the lifetime in seconds (RFC 8030). */
    static Map<String, String> cabecerasWeb() {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put(URGENCIA, URGENCIA_ALTA);
        cabeceras.put("TTL", String.valueOf(VIGENCIA.toSeconds()));
        return cabeceras;
    }

    /**
     * The page a click on the web notification opens: the alert's screen of the PWA (hash route
     * {@code #/alerta/<id>}, as the e-mailed links), or the PWA itself; null without a PWA URL.
     */
    static String enlace(ContenidoDelAviso contenido, String enlaceWeb) {
        if (enlaceWeb == null || contenido.alertaId() == null) {
            return enlaceWeb;
        }
        return enlaceWeb + "#/alerta/" + contenido.alertaId();
    }

    /**
     * FCM's {@code webpush} block: high urgency, the notification the PWA's service worker shows and,
     * when configured, the link it opens on click ({@code fcm_options.link}, HTTPS only).
     */
    private static Map<String, Object> webpush(ContenidoDelAviso contenido, String enlaceWeb) {
        Map<String, Object> notificacion = new LinkedHashMap<>();
        notificacion.put("title", contenido.titulo());
        notificacion.put("body", contenido.cuerpo());
        notificacion.put("tag", contenido.grupo());
        if (contenido.urgente()) {
            notificacion.put("requireInteraction", true);
        }
        Map<String, Object> webpush = new LinkedHashMap<>();
        webpush.put("headers", cabecerasWeb());
        webpush.put("notification", notificacion);
        String enlace = enlace(contenido, enlaceWeb);
        if (enlace != null) {
            webpush.put("fcm_options", Map.of("link", enlace));
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

    /**
     * The notice's label, when it has one, is the iOS subtitle ({@code aps.alert.subtitle}); urgent
     * notices are {@code time-sensitive}, the others {@code active} (the default).
     */
    private static Map<String, Object> aps(ContenidoDelAviso contenido) {
        Map<String, Object> alerta = new LinkedHashMap<>();
        alerta.put("title", contenido.titulo());
        if (contenido.etiqueta() != null) {
            alerta.put("subtitle", contenido.etiqueta());
        }
        alerta.put("body", contenido.cuerpo());
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alerta);
        aps.put("sound", "default");
        aps.put("thread-id", contenido.grupo());
        aps.put("interruption-level", nivelDeInterrupcion(contenido));
        return aps;
    }

    static String nivelDeInterrupcion(ContenidoDelAviso contenido) {
        return contenido.urgente() ? "time-sensitive" : "active";
    }
}
