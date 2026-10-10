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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * web push blocks ({@link CargasPush}), and FCM applies the one of the token's platform.
 *
 * <ul>
 *   <li><b>Transient errors</b> ({@code UNAVAILABLE}, {@code INTERNAL}, {@code QUOTA_EXCEEDED}) are
 *       retried for the failed devices only, up to {@link #INTENTOS} sends, after FCM's
 *       {@code Retry-After} when it sends one (else 1 s, then 2 s), never waiting more than
 *       {@link #ESPERA_MAXIMA}.
 *   <li><b>Gone tokens</b> ({@code UNREGISTERED}, {@code SENDER_ID_MISMATCH}) are reported so their
 *       devices are deactivated. {@code INVALID_ARGUMENT} is not: FCM also answers it for a payload
 *       it rejects, which would deactivate every device; it is logged as an error.
 *   <li>Every send is logged with the device's platform and token fingerprint ({@link HuellaDeToken};
 *       never the token) and FCM's message id or error code.
 *   <li>When FCM accepts no device for any reason other than gone tokens, it throws
 *       {@link FallaDePush} so the notice is retried (CA-16.4).
 * </ul>
 */
class NotificadorPushFcm implements NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPushFcm.class);

    private static final Set<MessagingErrorCode> TOKEN_INVALIDO =
            EnumSet.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

    private static final Set<MessagingErrorCode> TRANSITORIO =
            EnumSet.of(MessagingErrorCode.UNAVAILABLE, MessagingErrorCode.INTERNAL, MessagingErrorCode.QUOTA_EXCEEDED);

    /** Sends of one message at most: the first and two retries [implementation choice]. */
    static final int INTENTOS = 3;

    /** Longest wait before a retry, whatever {@code Retry-After} says: a fall cannot wait longer. */
    static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);

    /** Waits between retries; a seam for tests. */
    interface Espera {
        void esperar(Duration duracion) throws InterruptedException;
    }

    private final MensajeriaFcm mensajeria;
    private final String enlaceWeb;
    private final Clock reloj;
    private final Espera espera;

    /**
     * @param enlaceWeb the PWA's URL that web notifications open ({@link PropiedadesDePushWeb}); null
     *     sends none
     */
    NotificadorPushFcm(MensajeriaFcm mensajeria, String enlaceWeb, Clock reloj, Espera espera) {
        this.mensajeria = mensajeria;
        this.enlaceWeb = enlaceWeb;
        this.reloj = reloj;
        this.espera = espera;
    }

    NotificadorPushFcm(MensajeriaFcm mensajeria, String enlaceWeb, Clock reloj) {
        this(mensajeria, enlaceWeb, reloj, duracion -> Thread.sleep(duracion));
    }

    @Override
    public Resultado enviar(List<Destino> destinos, Aviso aviso) {
        ContenidoDelAviso contenido = ContenidoDelAviso.de(aviso);
        Instant ahora = reloj.instant();
        List<Message> mensajes = destinos.stream()
                .map(d -> mensaje(d.tokenPush(), contenido, enlaceWeb, ahora))
                .toList();
        MensajeriaFcm.Respuesta[] respuestas = enviarConReintentos(mensajes, aviso);
        int aceptados = 0;
        Set<String> invalidos = new HashSet<>();
        Set<MessagingErrorCode> errores = new HashSet<>();
        for (int i = 0; i < respuestas.length; i++) {
            MensajeriaFcm.Respuesta respuesta = respuestas[i];
            Destino destino = destinos.get(i);
            String dispositivo = destino.plataforma() + " " + HuellaDeToken.de(destino.tokenPush());
            if (respuesta.aceptada()) {
                aceptados++;
                log.info(
                        "Push {} (alerta {}) aceptado por FCM para {}: {}",
                        aviso.tipo(),
                        aviso.alertaId(),
                        dispositivo,
                        respuesta.idDelMensaje());
            } else if (TOKEN_INVALIDO.contains(respuesta.error())) {
                invalidos.add(destino.tokenPush());
                log.warn(
                        "Push {} (alerta {}) rechazado por FCM para {}: {}; el token ya no existe",
                        aviso.tipo(),
                        aviso.alertaId(),
                        dispositivo,
                        respuesta.error());
            } else if (respuesta.error() == MessagingErrorCode.INVALID_ARGUMENT) {
                errores.add(respuesta.error());
                log.error(
                        "Push {} (alerta {}) rechazado por FCM para {}: INVALID_ARGUMENT (carga o token inválido); el dispositivo sigue activo",
                        aviso.tipo(),
                        aviso.alertaId(),
                        dispositivo);
            } else {
                errores.add(respuesta.error());
                log.warn(
                        "Push {} (alerta {}) no aceptado por FCM para {}: {}",
                        aviso.tipo(),
                        aviso.alertaId(),
                        dispositivo,
                        respuesta.error());
            }
        }
        if (aceptados == 0 && !errores.isEmpty()) {
            throw new FallaDePush("FCM no aceptó el push " + aviso.tipo() + ": " + errores, null);
        }
        return new Resultado(aceptados, invalidos, Map.of());
    }

    /** One answer per message: the last one of each, after retrying the transient failures. */
    private MensajeriaFcm.Respuesta[] enviarConReintentos(List<Message> mensajes, Aviso aviso) {
        MensajeriaFcm.Respuesta[] finales = new MensajeriaFcm.Respuesta[mensajes.size()];
        List<Integer> pendientes = new ArrayList<>();
        for (int i = 0; i < mensajes.size(); i++) {
            pendientes.add(i);
        }
        for (int intento = 1; intento <= INTENTOS && !pendientes.isEmpty(); intento++) {
            List<MensajeriaFcm.Respuesta> respuestas;
            try {
                respuestas =
                        mensajeria.enviar(pendientes.stream().map(mensajes::get).toList());
            } catch (FirebaseMessagingException | RuntimeException e) {
                if (intento == 1) {
                    throw new FallaDePush("FCM no respondió al push " + aviso.tipo(), e);
                }
                log.warn("FCM no respondió al reintento {} del push {}", intento, aviso.tipo(), e);
                break;
            }
            List<Integer> reintentar = new ArrayList<>();
            Duration esperar = Duration.ofSeconds(1L << (intento - 1));
            for (int k = 0; k < pendientes.size(); k++) {
                MensajeriaFcm.Respuesta respuesta = respuestas.get(k);
                finales[pendientes.get(k)] = respuesta;
                if (!respuesta.aceptada() && TRANSITORIO.contains(respuesta.error())) {
                    reintentar.add(pendientes.get(k));
                    if (respuesta.reintentarTras() != null
                            && respuesta.reintentarTras().compareTo(esperar) > 0) {
                        esperar = respuesta.reintentarTras();
                    }
                }
            }
            pendientes = reintentar;
            if (pendientes.isEmpty() || intento == INTENTOS) {
                break;
            }
            Duration pausa = esperar.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : esperar;
            log.info(
                    "Push {}: FCM falló para {} dispositivo(s) por un error transitorio; reintento en {} ms",
                    aviso.tipo(),
                    pendientes.size(),
                    pausa.toMillis());
            try {
                espera.esperar(pausa);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return finales;
    }

    /** The same payload as {@link CargasPush#mensajeFcm}, built with the Admin SDK. */
    static Message mensaje(String token, ContenidoDelAviso contenido, String enlaceWeb, Instant ahora) {
        AndroidNotification.Builder notificacionAndroid =
                AndroidNotification.builder().setSound("default").setTag(contenido.grupo());
        if (contenido.alertaId() != null) {
            notificacionAndroid.setChannelId(CargasPush.CANAL_ALERTAS);
        }
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(contenido.titulo())
                        .setBody(contenido.cuerpo())
                        .build())
                .putAllData(contenido.datos())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(CargasPush.VIGENCIA.toMillis())
                        .setNotification(notificacionAndroid.build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putAllHeaders(CargasPush.cabecerasApns(contenido, ahora))
                        .setAps(Aps.builder()
                                .setAlert(alertaApns(contenido))
                                .setSound("default")
                                .setThreadId(contenido.grupo())
                                .putCustomData("interruption-level", CargasPush.nivelDeInterrupcion(contenido))
                                .build())
                        .build())
                .setWebpushConfig(webpush(contenido, enlaceWeb))
                .build();
    }

    private static WebpushConfig webpush(ContenidoDelAviso contenido, String enlaceWeb) {
        WebpushNotification.Builder notificacion = WebpushNotification.builder()
                .setTitle(contenido.titulo())
                .setBody(contenido.cuerpo())
                .setTag(contenido.grupo());
        if (contenido.urgente()) {
            notificacion.setRequireInteraction(true);
        }
        WebpushConfig.Builder webpush =
                WebpushConfig.builder().putAllHeaders(CargasPush.cabecerasWeb()).setNotification(notificacion.build());
        String enlace = CargasPush.enlace(contenido, enlaceWeb);
        if (enlace != null) {
            webpush.setFcmOptions(WebpushFcmOptions.withLink(enlace));
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
