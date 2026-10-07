package tech.tetengo.api.shared.infrastructure.security;

import static org.springframework.http.HttpMethod.POST;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tech.tetengo.api.shared.infrastructure.multitenancy.FiltroHogarActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/**
 * Stateless API: every request carries a JWT (RS256). The public key is configured with
 * {@code spring.security.oauth2.resourceserver.jwt.public-key-location}. Public endpoints are the
 * ones the API contract marks as {@code public}.
 */
@Configuration
public class SeguridadConfig {

    private static final String API = ApiVersioning.BASE;

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(POST, API + "/cuentas")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .addFilterAfter(new FiltroHogarActual(), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
