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
 * Lee el claim {@value #CLAIM_HOGAR} del JWT ya validado y fija el hogar de la petición. Lo usan tanto
 * los tokens del familiar como el token de cada cámara (el agente de la vivienda).
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
