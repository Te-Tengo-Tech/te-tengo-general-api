package tech.tetengo.api.shared.infrastructure.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.Rol;

/**
 * Who is calling, read from the validated JWT. Controllers use it to pass the caller to use cases and
 * to enforce owner-only endpoints ({@code 403 SOLO_TITULAR}, CA-08.4). The household itself is never
 * read from here by use cases: Hibernate filters it ({@code docs/MULTITENANCY.md}).
 */
public final class UsuarioActual {

    private UsuarioActual() {}

    /** The {@code sub} claim: user id for family members, camera id for agents. */
    public static UUID id() {
        return UUID.fromString(jwt().getSubject());
    }

    public static Optional<UUID> sesionId() {
        return uuid(ClaimsDelToken.SESION);
    }

    public static Optional<UUID> hogarId() {
        return uuid(ClaimsDelToken.HOGAR);
    }

    public static Optional<UUID> camaraId() {
        return uuid(ClaimsDelToken.CAMARA);
    }

    public static Optional<Rol> rol() {
        return Optional.ofNullable(jwt().getClaimAsString(ClaimsDelToken.ROL)).map(Rol::valueOf);
    }

    /** Owner-only endpoints: profile, consent, camera name, family and alert order (CA-08.4). */
    public static void exigirTitular() {
        if (rol().filter(Rol.TITULAR::equals).isEmpty()) {
            throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
        }
    }

    private static Optional<UUID> uuid(String claim) {
        return Optional.ofNullable(jwt().getClaimAsString(claim)).map(UUID::fromString);
    }

    private static Jwt jwt() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new IllegalStateException("La petición no está autenticada con un JWT");
    }
}
