package tech.tetengo.api.shared.infrastructure.security;

import static org.springframework.http.HttpMethod.POST;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tech.tetengo.api.shared.infrastructure.multitenancy.FiltroHogarActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/**
 * Stateless API: every request carries a JWT (RS256). The public key is configured with
 * {@code spring.security.oauth2.resourceserver.jwt.public-key-location}. Public endpoints are the
 * ones the API contract marks as {@code public}. The {@code rol} claim becomes the authority
 * {@code ROLE_<rol>}.
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
                        .requestMatchers(POST, API + "/cuentas", API + "/sesiones", API + "/sesiones/refresco")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeRoles())))
                .addFilterAfter(new FiltroHogarActual(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    private static JwtAuthenticationConverter convertidorDeRoles() {
        var autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName(ClaimsDelToken.ROL);
        autoridades.setAuthorityPrefix("ROLE_");
        var convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }
}
