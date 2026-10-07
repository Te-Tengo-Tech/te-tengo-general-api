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
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Firma JWT de prueba con un par de claves RSA generado en memoria (nunca se guarda en el repo). */
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

    /** Token de un familiar del hogar indicado. */
    public static String tokenDeFamiliar(UUID hogarId) {
        try {
            var claims = JwtClaimsSet.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(600))
                    .claim("hogar_id", hogarId.toString())
                    .build();
            var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(CLAVE)));
            var cabecera = JwsHeader.with(SignatureAlgorithm.RS256).build();
            return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
