package tech.tetengo.api.shared.infrastructure.web.limites;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link FiltroDeLimitePorIp} unless {@code tetengo.limites.habilitado=false}. It runs
 * after the character encoding filter and before Spring Security's chain (order -100), so a client
 * over its limit costs no authentication and no database work.
 */
@Configuration
@ConditionalOnProperty(name = "tetengo.limites.habilitado", havingValue = "true", matchIfMissing = true)
public class LimitesConfig {

    static final int ORDEN = Ordered.HIGHEST_PRECEDENCE + 100;

    @Bean
    LimitadorDeSolicitudes limitadorDeSolicitudes(Clock reloj, PropiedadesDeLimites propiedades) {
        return new LimitadorDeSolicitudes(reloj, propiedades.maxDirecciones());
    }

    @Bean
    FilterRegistrationBean<FiltroDeLimitePorIp> filtroDeLimitePorIp(
            LimitadorDeSolicitudes limitador, PropiedadesDeLimites propiedades) {
        var registro = new FilterRegistrationBean<>(new FiltroDeLimitePorIp(limitador, propiedades));
        registro.setName("filtroDeLimitePorIp");
        registro.addUrlPatterns("/api/*");
        registro.setOrder(ORDEN);
        return registro;
    }
}
