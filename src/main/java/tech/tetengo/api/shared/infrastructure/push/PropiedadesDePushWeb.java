package tech.tetengo.api.shared.infrastructure.push;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Web push to the PWA build of the app (platform {@code WEB}), sent by the {@code fcm} provider and by
 * {@code sns} when it has a web platform application.
 *
 * @param enlace the PWA's URL ({@code TT_PWA_URL}), opened when the notification is clicked
 *     ({@code webpush.fcm_options.link}); blank sends no link. FCM only accepts HTTPS links and rejects
 *     the whole message otherwise, which would deactivate every web device, so any other value stops
 *     the API at startup.
 */
@ConfigurationProperties("tetengo.push.web")
public record PropiedadesDePushWeb(String enlace) {

    public PropiedadesDePushWeb {
        enlace = StringUtils.hasText(enlace) ? enlace.strip() : null;
        if (enlace != null && !enlace.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "tetengo.push.web.enlace (TT_PWA_URL) debe ser una URL https://, como exige FCM: " + enlace);
        }
    }
}
