package tech.tetengo.api.shared.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing key of the tokens this API issues. The private key is configured like the public key
 * ({@code tetengo.jwt.clave-privada-location}, PKCS#8 PEM; locally {@code scripts/generate-keys.sh}).
 * Tests leave it empty and sign with {@code JwtDePrueba}.
 */
@Configuration
@ConditionalOnExpression("!'${tetengo.jwt.clave-privada-location:}'.isBlank()")
public class ClavesJwtConfig {

    @Bean
    JwtEncoder codificadorJwt(
            @Value("${tetengo.jwt.clave-privada-location}") Resource privada,
            @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}") Resource publica)
            throws IOException {
        RSAKey clave = new RSAKey.Builder(leerPublica(publica))
                .privateKey(leerPrivada(privada))
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(clave)));
    }

    private static RSAPublicKey leerPublica(Resource recurso) throws IOException {
        try (InputStream entrada = recurso.getInputStream()) {
            return RsaKeyConverters.x509().convert(entrada);
        }
    }

    private static RSAPrivateKey leerPrivada(Resource recurso) throws IOException {
        try (InputStream entrada = recurso.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(entrada);
        }
    }
}
