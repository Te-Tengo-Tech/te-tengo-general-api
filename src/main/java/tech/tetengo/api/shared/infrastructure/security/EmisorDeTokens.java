package tech.tetengo.api.shared.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Signs RS256 JWTs for family members ({@code cuentas}) and household agents ({@code camaras}). */
@Component
public class EmisorDeTokens {

    private final JwtEncoder codificador;
    private final Clock reloj;

    public EmisorDeTokens(JwtEncoder codificador, Clock reloj) {
        this.codificador = codificador;
        this.reloj = reloj;
    }

    /** Claims with a {@code null} value are left out of the token. */
    public TokenEmitido emitir(String sujeto, Map<String, Object> claims, Duration vigencia) {
        // JWT times have second precision; expiraEn must match the exp claim exactly.
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expira = ahora.plus(vigencia);
        JwtClaimsSet.Builder contenido =
                JwtClaimsSet.builder().subject(sujeto).issuedAt(ahora).expiresAt(expira);
        claims.forEach((nombre, valor) -> {
            if (valor != null) {
                contenido.claim(nombre, valor.toString());
            }
        });
        var cabecera = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String valor = codificador
                .encode(JwtEncoderParameters.from(cabecera, contenido.build()))
                .getTokenValue();
        return new TokenEmitido(valor, expira);
    }
}
