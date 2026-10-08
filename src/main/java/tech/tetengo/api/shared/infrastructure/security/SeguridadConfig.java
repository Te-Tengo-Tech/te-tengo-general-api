package tech.tetengo.api.shared.infrastructure.security;

import static org.springframework.http.HttpMethod.POST;

import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import tech.tetengo.api.shared.application.port.ComprobadorDeMembresia;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.multitenancy.FiltroHogarActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/**
 * Stateless API: every request carries a JWT (RS256). The public key is configured with
 * {@code spring.security.oauth2.resourceserver.jwt.public-key-location}. Public endpoints are the
 * ones the API contract marks as {@code public}. The {@code rol} claim becomes the authority
 * {@code ROLE_<rol>}: household agent tokens only open {@code /api/agente/**}, and family members'
 * tokens never do. A missing, expired or invalid token answers {@code 401 SESION_EXPIRADA}
 * ({@link EntradaSinSesion}).
 */
@Configuration
public class SeguridadConfig {

    private static final String API = ApiVersioning.BASE;
    private static final String AUTORIDAD_AGENTE = "ROLE_" + Rol.AGENTE.name();

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ObjectProvider<ComprobadorDeMembresia> membresias)
            throws Exception {
        var sinSesion = new EntradaSinSesion();
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(
                                POST,
                                API + "/cuentas",
                                API + "/sesiones",
                                API + "/sesiones/refresco",
                                API + "/recuperaciones",
                                API + "/recuperaciones/confirmacion",
                                API + "/agente/camaras/registro",
                                API + "/invitaciones/*/aceptacion",
                                // MediaMTX's authorization hook: checked by its shared secret.
                                API + "/interno/mediamtx/autorizar")
                        .permitAll()
                        .requestMatchers(API + "/agente/**")
                        .hasRole(Rol.AGENTE.name())
                        .anyRequest()
                        .access(usuarioQueNoEsAgente()))
                .exceptionHandling(e -> e.authenticationEntryPoint(sinSesion))
                .oauth2ResourceServer(rs -> rs.authenticationEntryPoint(sinSesion)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeRoles())))
                .addFilterAfter(
                        new FiltroHogarActual(membresias::getIfAvailable), BearerTokenAuthenticationFilter.class)
                .build();
    }

    /** Authenticated family member (any token except a household agent's). */
    private static AuthorizationManager<RequestAuthorizationContext> usuarioQueNoEsAgente() {
        return new AuthorizationManager<>() {
            @Override
            public AuthorizationResult authorize(
                    Supplier<? extends Authentication> autenticacion, RequestAuthorizationContext contexto) {
                Authentication actual = autenticacion.get();
                boolean autenticado =
                        actual != null && actual.isAuthenticated() && !(actual instanceof AnonymousAuthenticationToken);
                boolean agente = autenticado
                        && actual.getAuthorities().stream().anyMatch(a -> AUTORIDAD_AGENTE.equals(a.getAuthority()));
                return new AuthorizationDecision(autenticado && !agente);
            }
        };
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
