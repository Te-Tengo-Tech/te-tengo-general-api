package tech.tetengo.api.shared.infrastructure.push;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.tetengo.api.shared.application.port.NotificadorPush;

/**
 * Chooses the push adapter with {@code tetengo.push.proveedor}: {@code registro} (default, only
 * logs), {@code fcm} (Firebase Cloud Messaging), {@code sns} (Amazon SNS mobile push) or
 * {@code simulador} (the booted iOS simulator, local only). Any other value leaves no adapter, so the
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
    NotificadorPush notificadorPushFcm(PropiedadesDeFcm propiedades) {
        return new NotificadorPushFcm(MensajeriaFirebase.crear(propiedades));
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "sns")
    NotificadorPush notificadorPushSns(PropiedadesDeSns propiedades) {
        return NotificadorPushSns.crear(propiedades);
    }
}
