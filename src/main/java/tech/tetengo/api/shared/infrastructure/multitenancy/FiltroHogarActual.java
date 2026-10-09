package tech.tetengo.api.shared.infrastructure.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tech.tetengo.api.shared.application.port.ComprobadorDeMembresia;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.security.ClaimsDelToken;

/**
 * Reads the {@value #CLAIM_HOGAR} claim of the validated JWT and sets the request household. Both
 * family-member tokens and per-camera tokens (the household agent) carry it. A family member's
 * household is only bound while they are still a member, so removing someone (CA-08.3) takes effect
 * at once: with no household in context every query fails closed.
 */
public class FiltroHogarActual extends OncePerRequestFilter {

    public static final String CLAIM_HOGAR = ClaimsDelToken.HOGAR;

    private final Supplier<ComprobadorDeMembresia> membresias;

    public FiltroHogarActual(Supplier<ComprobadorDeMembresia> membresias) {
        this.membresias = membresias;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
                Jwt jwt = token.getToken();
                String hogar = jwt.getClaimAsString(CLAIM_HOGAR);
                if (hogar != null && sigueSiendoMiembro(jwt, UUID.fromString(hogar))) {
                    HogarActual.fijar(UUID.fromString(hogar));
                }
            }
            cadena.doFilter(peticion, respuesta);
        } finally {
            HogarActual.limpiar();
        }
    }

    private boolean sigueSiendoMiembro(Jwt jwt, UUID hogar) {
        String rol = jwt.getClaimAsString(ClaimsDelToken.ROL);
        if (Rol.AGENTE.name().equals(rol)) {
            return true;
        }
        ComprobadorDeMembresia comprobador = membresias.get();
        return comprobador == null || comprobador.esMiembro(hogar, UUID.fromString(jwt.getSubject()));
    }
}
