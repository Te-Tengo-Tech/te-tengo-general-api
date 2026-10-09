package tech.tetengo.api.shared.infrastructure.web;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin access to {@code /api/**} for browser clients, the PWA build of the app. The native
 * app and the household agent are not browsers and need none.
 *
 * @param origenes origins (scheme, host and port, never a path) or origin patterns allowed to call the
 *     API, e.g. {@code https://te-tengo.pages.dev} or {@code http://localhost:*}
 *     ({@code TT_CORS_ORIGENES}, comma-separated). Empty, the default outside the {@code local}
 *     profile: CORS is off and browsers on other origins cannot call the API.
 */
@ConfigurationProperties("tetengo.cors")
public record PropiedadesDeCors(List<String> origenes) {

    /** The headers the app sends: the bearer token, the API version and JSON bodies. */
    static final List<String> CABECERAS_PERMITIDAS =
            List.of(HttpHeaders.AUTHORIZATION, ApiVersioning.CABECERA, HttpHeaders.CONTENT_TYPE);

    /** The app may read why a token was rejected; the rest of what it reads is CORS-safelisted. */
    static final List<String> CABECERAS_EXPUESTAS = List.of(HttpHeaders.WWW_AUTHENTICATE);

    /** Browsers cache a preflight answer this long [implementation choice]. */
    static final Duration VIGENCIA_PREFLIGHT = Duration.ofHours(1);

    public PropiedadesDeCors {
        origenes = origenes == null
                ? List.of()
                : origenes.stream().map(String::strip).filter(o -> !o.isEmpty()).toList();
    }

    /**
     * The CORS rules of {@code /api/**}, or empty when no origin is allowed. Bearer tokens travel in
     * a header, never in cookies, so credentials are not allowed.
     */
    public Optional<CorsConfigurationSource> fuente() {
        if (origenes.isEmpty()) {
            return Optional.empty();
        }
        var reglas = new CorsConfiguration();
        reglas.setAllowedOriginPatterns(origenes);
        reglas.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name()));
        reglas.setAllowedHeaders(CABECERAS_PERMITIDAS);
        reglas.setExposedHeaders(CABECERAS_EXPUESTAS);
        reglas.setAllowCredentials(false);
        reglas.setMaxAge(VIGENCIA_PREFLIGHT);
        var fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration(ApiVersioning.BASE + "/**", reglas);
        return Optional.of(fuente);
    }
}
