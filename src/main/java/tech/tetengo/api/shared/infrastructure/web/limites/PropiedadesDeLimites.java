package tech.tetengo.api.shared.infrastructure.web.limites;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import tech.tetengo.api.shared.infrastructure.web.limites.LimitadorDeSolicitudes.Limite;

/**
 * Per-client-address limits of the public endpoints anyone can call without a token
 * ({@link FiltroDeLimitePorIp}). All values are implementation choices; {@code 0} turns a bucket off.
 *
 * @param habilitado {@code false} turns every limit off ({@code TT_LIMITES_HABILITADO}; integration
 *     tests do, except the ones about limits)
 * @param maxDirecciones how many client addresses are tracked at once (memory bound)
 * @param vinculacion {@code POST /api/agente/vinculacion}: a PC asks for a new code every 10 minutes
 *     while nobody links it, so 10 per minute and 60 per hour leave room for several PCs behind one
 *     address (carrier-grade NAT is common)
 * @param token {@code POST /api/agente/token}: a PC polls every 5 s (12 per minute) and renews its
 *     token once an hour; 60 per minute is five PCs linking at once behind one address
 * @param sesiones {@code POST /api/sesiones}: the account lockout (CA-02.3) stops guessing one
 *     account; this stops trying many accounts from one address
 * @param recuperaciones {@code POST /api/recuperaciones}: each one may send an e-mail
 */
@ConfigurationProperties("tetengo.limites")
public record PropiedadesDeLimites(
        @DefaultValue("true") boolean habilitado,
        @DefaultValue("100000") int maxDirecciones,
        PorDireccion vinculacion,
        PorDireccion token,
        PorDireccion sesiones,
        PorDireccion recuperaciones) {

    public PropiedadesDeLimites {
        vinculacion = vinculacion != null ? vinculacion : new PorDireccion(10, 60);
        token = token != null ? token : new PorDireccion(60, 0);
        sesiones = sesiones != null ? sesiones : new PorDireccion(20, 100);
        recuperaciones = recuperaciones != null ? recuperaciones : new PorDireccion(5, 20);
    }

    /** Requests per minute and per hour from one client address; {@code 0} is no limit. */
    public record PorDireccion(
            @DefaultValue("0") int porMinuto,
            @DefaultValue("0") int porHora) {

        List<Limite> limites() {
            List<Limite> limites = new ArrayList<>(2);
            if (porMinuto > 0) {
                limites.add(new Limite(porMinuto, Duration.ofMinutes(1)));
            }
            if (porHora > 0) {
                limites.add(new Limite(porHora, Duration.ofHours(1)));
            }
            return List.copyOf(limites);
        }
    }
}
