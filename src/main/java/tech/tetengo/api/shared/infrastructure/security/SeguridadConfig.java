package tech.tetengo.api.shared.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tech.tetengo.api.shared.infrastructure.multitenancy.FiltroHogarActual;

/**
 * API sin sesión: cada petición trae un JWT (RS256). La clave pública se configura con
 * {@code spring.security.oauth2.resourceserver.jwt.public-key-location}.
 */
@Configuration
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .addFilterAfter(new FiltroHogarActual(), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
