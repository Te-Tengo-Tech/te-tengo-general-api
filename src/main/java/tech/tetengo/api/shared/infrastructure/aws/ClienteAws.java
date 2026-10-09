package tech.tetengo.api.shared.infrastructure.aws;

import java.net.URI;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/**
 * Connection settings shared by the AWS clients of the e-mail and push adapters: region, endpoint
 * (Floci locally, ADR 0005) and credentials. Blank values fall back to the AWS SDK defaults (region
 * chain, real AWS endpoints, default credentials chain such as an instance role).
 *
 * @param region AWS region; blank uses the SDK's default region chain
 * @param endpoint endpoint override (e.g. {@code http://localhost:4566}); blank uses AWS
 * @param accessKey static access key; blank uses the SDK's default credentials chain
 * @param secretKey static secret key, with {@code accessKey}
 */
public record ClienteAws(String region, URI endpoint, String accessKey, String secretKey) {

    public ClienteAws {
        if (endpoint != null && endpoint.toString().isBlank()) {
            endpoint = null;
        }
    }

    /** Applies the settings to any AWS SDK v2 client builder. */
    public <B extends AwsClientBuilder<B, ?>> B configurar(B builder) {
        builder.credentialsProvider(credenciales());
        if (StringUtils.hasText(region)) {
            builder.region(Region.of(region));
        }
        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }
        return builder;
    }

    private AwsCredentialsProvider credenciales() {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    /** For logs: where the client points. */
    public String destino() {
        return endpoint == null ? "AWS" : endpoint.toString();
    }
}
