package tech.tetengo.api.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tech.tetengo.api.shared.domain.model.Rol;

/**
 * Signs test JWTs with an in-memory RSA key pair (never stored in the repository). The application
 * signs its own tokens with the same key, so tokens issued by the API are valid in tests too.
 */
@TestConfiguration(proxyBeanMethods = false)
public class JwtDePrueba {

    private static final RSAKey CLAVE = generar();

    private static RSAKey generar() {
        try {
            return new RSAKeyGenerator(2048).keyID("pruebas").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Bean
    @Primary
    JwtDecoder decodificadorDePrueba() throws Exception {
        return NimbusJwtDecoder.withPublicKey(CLAVE.toRSAPublicKey()).build();
    }

    @Bean
    @Primary
    JwtEncoder codificadorDePrueba() {
        return codificador();
    }

    /**
     * Token of a family member. The household is only bound while the user is a member of it
     * (CA-08.3), so create the membership first (e.g. {@code DatosDePrueba.hogar}).
     */
    public static String token(UUID usuarioId, UUID hogarId, Rol rol) {
        var claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600));
        if (hogarId != null) {
            claims.claim("hogar_id", hogarId.toString());
        }
        if (rol != null) {
            claims.claim("rol", rol.name());
        }
        return firmar(claims.build());
    }

    /** A family member's token that expired ten minutes ago (beyond the decoder's clock skew). */
    public static String tokenVencido(UUID usuarioId, UUID hogarId, Rol rol) {
        return firmar(JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .issuedAt(Instant.now().minusSeconds(4200))
                .expiresAt(Instant.now().minusSeconds(600))
                .claim("hogar_id", hogarId.toString())
                .claim("rol", rol.name())
                .build());
    }

    /** Per-camera token of the household agent. */
    public static String tokenDeAgente(UUID hogarId, UUID camaraId) {
        return firmar(JwtClaimsSet.builder()
                .subject(camaraId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("hogar_id", hogarId.toString())
                .claim("camara_id", camaraId.toString())
                .claim("rol", Rol.AGENTE.name())
                .build());
    }

    private static String firmar(JwtClaimsSet claims) {
        var cabecera = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return codificador().encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    private static JwtEncoder codificador() {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(CLAVE)));
    }
}
