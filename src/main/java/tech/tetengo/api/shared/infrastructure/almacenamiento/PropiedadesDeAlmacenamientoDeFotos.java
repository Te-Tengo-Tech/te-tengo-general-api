package tech.tetengo.api.shared.infrastructure.almacenamiento;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Object storage of the profile photos (API contract §13): Cloudflare R2 through its S3 API, or
 * Amazon S3. By default the same bucket and credentials as the clips (application.yml). Without a
 * bucket, an in-memory fake is used.
 */
@ConfigurationProperties("tetengo.fotos.s3")
public record PropiedadesDeAlmacenamientoDeFotos(
        String bucket,
        String region,
        URI endpoint,
        boolean pathStyle,
        String accessKey,
        String secretKey,
        boolean crearBucket) {

    public PropiedadesDeAlmacenamientoDeFotos {
        if (endpoint != null && endpoint.toString().isBlank()) {
            endpoint = null;
        }
    }

    boolean conCredencialesFijas() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }
}
