package tech.tetengo.api.shared.infrastructure.push;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.tetengo.api.shared.application.port.NotificadorPush;

/**
 * Chooses the push adapter with {@code tetengo.push.proveedor}: {@code registro} (default, only
 * logs), {@code fcm} (Firebase Cloud Messaging, also web push to the PWA), {@code sns} (Amazon SNS
 * mobile push; web devices only with a web platform application) or {@code simulador} (the booted iOS
 * simulator, local only; it skips web devices). Any other value leaves no adapter, so the
 * API does not start. docs/NOTIFICATIONS.md
 */
@Configuration(proxyBeanMethods = false)
class NotificadorPushConfig {

    private static final String PREFIJO = "tetengo.push";
    private static final String PROVEEDOR = "proveedor";

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "registro", matchIfMissing = true)
    NotificadorPush notificadorPushEnRegistro() {
        return new NotificadorPushEnRegistro();
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "fcm")
    NotificadorPush notificadorPushFcm(
            PropiedadesDeFcm propiedades, PropiedadesDePushWeb web, ObjectProvider<Clock> reloj) {
        return new NotificadorPushFcm(
                MensajeriaFirebase.crear(propiedades), web.enlace(), reloj.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "sns")
    NotificadorPush notificadorPushSns(PropiedadesDeSns propiedades, PropiedadesDePushWeb web) {
        return NotificadorPushSns.crear(propiedades, web.enlace());
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "simulador")
    NotificadorPush notificadorPushSimulador(PropiedadesDelSimulador propiedades) {
        return new NotificadorPushSimulador(propiedades);
    }
}
