package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.application.port.TokenDeRenovacionRepository;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.camaras.domain.model.TokenDeRenovacionDePc;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.security.ClaimsDelToken;
import tech.tetengo.api.shared.infrastructure.security.EmisorDeTokens;
import tech.tetengo.api.shared.infrastructure.security.Secretos;
import tech.tetengo.api.shared.infrastructure.security.TokenEmitido;

/**
 * Tokens of a PC: its access token ({@code sub} = PC, {@code hogar_id}, {@code pc_id}, {@code rol =
 * AGENTE}) with a new refresh token, and the per-camera tokens of its cameras, which also carry
 * {@code pc_id} so they stop working when the PC is unlinked. Runs inside the PC's household.
 */
@Service
public class EmisionDeTokensDePc {

    private final PcRepository pcs;
    private final TokenDeRenovacionRepository tokens;
    private final EmisorDeTokens emisor;
    private final PropiedadesDeVinculacion propiedades;
    private final Clock reloj;

    public EmisionDeTokensDePc(
            PcRepository pcs,
            TokenDeRenovacionRepository tokens,
            EmisorDeTokens emisor,
            PropiedadesDeVinculacion propiedades,
            Clock reloj) {
        this.pcs = pcs;
        this.tokens = tokens;
        this.emisor = emisor;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** CA-28.1, CA-28.7: an access token and the next refresh token; the PC counts as in use. */
    public TokensDePc emitir(Pc pc, String cliente) {
        Instant ahora = reloj.instant();
        String renovacion = Secretos.generar();
        tokens.guardar(new TokenDeRenovacionDePc(
                pc.getId(),
                pc.getHogarId(),
                Secretos.huella(renovacion),
                cliente,
                ahora.plus(propiedades.vigenciaTokenRenovacion())));
        pc.registrarUso(ahora);
        pcs.guardar(pc);
        TokenEmitido acceso = emitirAcceso(pc.getId().toString(), pc.getHogarId(), pc.getId(), null);
        return new TokensDePc(
                acceso.valor(), propiedades.vigenciaTokenAcceso().toSeconds(), renovacion, pc.getId(), pc.getHogarId());
    }

    /** A per-camera token of the PC's camera, like an installation's but with {@code pc_id}. */
    public TokenEmitido deCamara(UUID hogarId, UUID pcId, UUID camaraId) {
        return emitirAcceso(camaraId.toString(), hogarId, pcId, camaraId);
    }

    private TokenEmitido emitirAcceso(String sujeto, UUID hogarId, UUID pcId, UUID camaraId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(ClaimsDelToken.HOGAR, hogarId);
        claims.put(ClaimsDelToken.PC, pcId);
        claims.put(ClaimsDelToken.CAMARA, camaraId);
        claims.put(ClaimsDelToken.ROL, Rol.AGENTE);
        return emisor.emitir(sujeto, claims, propiedades.vigenciaTokenAcceso());
    }
}
