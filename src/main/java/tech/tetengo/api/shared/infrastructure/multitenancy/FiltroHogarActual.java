package tech.tetengo.api.shared.infrastructure.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@value #CLAIM_HOGAR} claim of the validated JWT and sets the request household. Both
 * family-member tokens and per-camera tokens (the household agent) carry it.
 */
public class FiltroHogarActual extends OncePerRequestFilter {

    public static final String CLAIM_HOGAR = "hogar_id";

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
                String hogar = token.getToken().getClaimAsString(CLAIM_HOGAR);
                if (hogar != null) {
                    HogarActual.fijar(UUID.fromString(hogar));
                }
            }
            cadena.doFilter(peticion, respuesta);
        } finally {
            HogarActual.limpiar();
        }
    }
}
