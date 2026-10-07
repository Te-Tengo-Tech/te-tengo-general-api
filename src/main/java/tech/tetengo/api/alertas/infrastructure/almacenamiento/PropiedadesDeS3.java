package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Amazon S3 (or an S3-compatible store) for the clips. The S3 adapter is used only when
 * {@code bucket} is set; otherwise the in-memory fake stays.
 *
 * @param bucket bucket of the clips
 * @param region AWS region; blank uses the SDK's default region chain
 * @param endpoint endpoint override for an S3-compatible store (e.g. SeaweedFS locally); blank uses
 *     AWS. It is also the host of the pre-signed URLs, so the agent and the app must reach it
 * @param pathStyle path-style URLs ({@code endpoint/bucket/key}), needed by most local stores
 * @param accessKey static access key; blank uses the SDK's default credentials chain
 * @param secretKey static secret key, with {@code accessKey}
 * @param crearBucket create the bucket at startup if it is missing (local runs only)
 */
@ConfigurationProperties("tetengo.clips.s3")
public record PropiedadesDeS3(
        String bucket,
        String region,
        URI endpoint,
        boolean pathStyle,
        String accessKey,
        String secretKey,
        boolean crearBucket) {

    public PropiedadesDeS3 {
        if (endpoint != null && endpoint.toString().isBlank()) {
            endpoint = null;
        }
    }

    boolean conCredencialesFijas() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }
}
