package tech.tetengo.api.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

/**
 * A protected endpoint reached with a missing, expired or invalid access token answers {@code 401}
 * {@code application/problem+json} with {@code codigo: SESION_EXPIRADA} (API contract, conventions):
 * the app refreshes its tokens only when it sees that code. The {@code WWW-Authenticate} header of
 * RFC 6750 is kept.
 */
public class EntradaSinSesion implements AuthenticationEntryPoint {

    public static final String SESION_EXPIRADA = "SESION_EXPIRADA";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException causa)
            throws IOException {
        // Sets 401 and WWW-Authenticate (with error="invalid_token" when a token was rejected).
        bearer.commence(peticion, respuesta, causa);
        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("type", "about:blank");
        problema.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        problema.put("status", HttpStatus.UNAUTHORIZED.value());
        problema.put("detail", "Tu sesión expiró. Vuelve a iniciar sesión.");
        problema.put("instance", peticion.getRequestURI());
        problema.put("codigo", SESION_EXPIRADA);
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        JSON.writeValue(respuesta.getOutputStream(), problema);
    }
}
