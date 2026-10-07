package tech.tetengo.api.shared.infrastructure.correo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/**
 * Chooses the e-mail adapter with {@code tetengo.correo.proveedor}: {@code registro} (default, only
 * logs) or {@code ses} (Amazon SES). Any other value leaves no adapter, so the API does not start.
 */
@Configuration(proxyBeanMethods = false)
class NotificadorCorreoConfig {

    private static final String PREFIJO = "tetengo.correo";
    private static final String PROVEEDOR = "proveedor";

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "registro", matchIfMissing = true)
    NotificadorCorreo notificadorCorreoEnRegistro() {
        return new NotificadorCorreoEnRegistro();
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO, name = PROVEEDOR, havingValue = "ses")
    NotificadorCorreo notificadorCorreoEnSes(PropiedadesDeSes propiedades) {
        return NotificadorCorreoEnSes.crear(propiedades);
    }
}
