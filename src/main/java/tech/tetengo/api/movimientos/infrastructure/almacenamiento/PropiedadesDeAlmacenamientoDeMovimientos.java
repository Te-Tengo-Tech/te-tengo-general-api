package tech.tetengo.api.movimientos.infrastructure.almacenamiento;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Object storage of the movement files: Cloudflare R2 through its S3 API (or Amazon S3). By default the
 * same bucket and credentials as the clips (application.yml); a bucket of its own can be set, e.g. with
 * its own lifecycle rules. Without a bucket the in-memory fake is used.
 */
@ConfigurationProperties("tetengo.movimientos.s3")
public record PropiedadesDeAlmacenamientoDeMovimientos(
        String bucket,
        String region,
        URI endpoint,
        boolean pathStyle,
        String accessKey,
        String secretKey,
        boolean crearBucket) {

    public PropiedadesDeAlmacenamientoDeMovimientos {
        if (endpoint != null && endpoint.toString().isBlank()) {
            endpoint = null;
        }
    }

    boolean conCredencialesFijas() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }
}
