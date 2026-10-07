package tech.tetengo.api.shared.infrastructure.correo;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import tech.tetengo.api.shared.infrastructure.aws.ClienteAws;

/**
 * Amazon SES, used when {@code tetengo.correo.proveedor=ses}.
 *
 * @param remitente sender address, a verified SES identity (e.g. {@code Te Tengo
 *     <no-responder@example.com>}); required
 * @param region AWS region; blank uses the SDK's default region chain
 * @param endpoint endpoint override (Floci locally); blank uses AWS
 * @param accessKey static access key; blank uses the SDK's default credentials chain
 * @param secretKey static secret key, with {@code accessKey}
 */
@ConfigurationProperties("tetengo.correo.ses")
public record PropiedadesDeSes(String remitente, String region, URI endpoint, String accessKey, String secretKey) {

    ClienteAws cliente() {
        return new ClienteAws(region, endpoint, accessKey, secretKey);
    }
}
