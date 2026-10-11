package tech.tetengo.api.shared.infrastructure.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tech.tetengo.api.shared.application.port.ComprobadorDeAgentes;
import tech.tetengo.api.shared.application.port.ComprobadorDeMembresia;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.security.ClaimsDelToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the {@value #CLAIM_HOGAR} claim of the validated JWT and sets the request household. Both
 * family-member tokens and household agent tokens carry it.
 *
 * <ul>
 *   <li>A family member's household is only bound while they are still a member, so removing someone
 *       (CA-08.3) takes effect at once: with no household in context every query fails closed.
 *   <li>A household agent token is only accepted while its PC is still linked and its camera is still
 *       in use (US-31): otherwise the request ends here with {@code 401 PC_DESVINCULADA}, so the agent
 *       knows it must delete its credentials and show the link code again.
 * </ul>
 */
public class FiltroHogarActual extends OncePerRequestFilter {

    public static final String CLAIM_HOGAR = ClaimsDelToken.HOGAR;

    /** Code of the {@code 401} answered to the token of an unlinked PC or a retired camera. */
    public static final String PC_DESVINCULADA = "PC_DESVINCULADA";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Supplier<ComprobadorDeMembresia> membresias;
    private final Supplier<ComprobadorDeAgentes> agentes;

    public FiltroHogarActual(Supplier<ComprobadorDeMembresia> membresias) {
        this(membresias, () -> null);
    }

    public FiltroHogarActual(Supplier<ComprobadorDeMembresia> membresias, Supplier<ComprobadorDeAgentes> agentes) {
        this.membresias = membresias;
        this.agentes = agentes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
                Jwt jwt = token.getToken();
                String hogar = jwt.getClaimAsString(CLAIM_HOGAR);
                if (hogar != null) {
                    UUID hogarId = UUID.fromString(hogar);
                    if (esAgente(jwt)) {
                        if (!agenteAutorizado(jwt, hogarId)) {
                            rechazarAgente(peticion, respuesta);
                            return;
                        }
                        HogarActual.fijar(hogarId);
                    } else if (sigueSiendoMiembro(jwt, hogarId)) {
                        HogarActual.fijar(hogarId);
                    }
                }
            }
            cadena.doFilter(peticion, respuesta);
        } finally {
            HogarActual.limpiar();
        }
    }

    private static boolean esAgente(Jwt jwt) {
        return Rol.AGENTE.name().equals(jwt.getClaimAsString(ClaimsDelToken.ROL));
    }

    private boolean agenteAutorizado(Jwt jwt, UUID hogar) {
        ComprobadorDeAgentes comprobador = agentes.get();
        return comprobador == null
                || comprobador.autorizado(hogar, uuid(jwt, ClaimsDelToken.PC), uuid(jwt, ClaimsDelToken.CAMARA));
    }

    private boolean sigueSiendoMiembro(Jwt jwt, UUID hogar) {
        ComprobadorDeMembresia comprobador = membresias.get();
        return comprobador == null || comprobador.esMiembro(hogar, UUID.fromString(jwt.getSubject()));
    }

    private static UUID uuid(Jwt jwt, String claim) {
        String valor = jwt.getClaimAsString(claim);
        return valor == null ? null : UUID.fromString(valor);
    }

    /** RFC 9457 problem with {@code codigo: PC_DESVINCULADA}, as {@code EntradaSinSesion} answers. */
    private static void rechazarAgente(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("type", "about:blank");
        problema.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        problema.put("status", HttpStatus.UNAUTHORIZED.value());
        problema.put("detail", "Esta PC ya no está vinculada a la vivienda. Vuelve a vincularla desde la app.");
        problema.put("instance", peticion.getRequestURI());
        problema.put("codigo", PC_DESVINCULADA);
        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        JSON.writeValue(respuesta.getOutputStream(), problema);
    }
}
