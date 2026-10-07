package tech.tetengo.api.shared.infrastructure.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Cloud Messaging, used when {@code tetengo.push.proveedor=fcm}.
 *
 * @param credenciales path of the Firebase service-account JSON key ({@code TT_FCM_CREDENCIALES});
 *     never committed
 */
@ConfigurationProperties("tetengo.push.fcm")
public record PropiedadesDeFcm(String credenciales) {}
