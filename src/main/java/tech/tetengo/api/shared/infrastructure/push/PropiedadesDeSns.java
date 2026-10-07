package tech.tetengo.api.shared.infrastructure.push;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import tech.tetengo.api.shared.infrastructure.aws.ClienteAws;

/**
 * Amazon SNS mobile push, used when {@code tetengo.push.proveedor=sns}.
 *
 * @param arnAndroid platform application of Android devices, an FCM ({@code GCM}) application
 *     ({@code TT_SNS_ARN_ANDROID})
 * @param arnIos platform application of iOS devices ({@code TT_SNS_ARN_IOS}): an FCM application
 *     while the app registers FCM tokens on iOS (it may be the same as {@code arnAndroid}), or an
 *     {@code APNS} / {@code APNS_SANDBOX} application for APNs device tokens
 * @param region AWS region; blank uses the SDK's default region chain
 * @param endpoint endpoint override (Floci locally); blank uses AWS
 * @param accessKey static access key; blank uses the SDK's default credentials chain
 * @param secretKey static secret key, with {@code accessKey}
 * @param crearAplicaciones create the platform applications at startup when their ARNs are blank
 *     (local runs on Floci only)
 */
@ConfigurationProperties("tetengo.push.sns")
public record PropiedadesDeSns(
        String arnAndroid,
        String arnIos,
        String region,
        URI endpoint,
        String accessKey,
        String secretKey,
        boolean crearAplicaciones) {

    ClienteAws cliente() {
        return new ClienteAws(region, endpoint, accessKey, secretKey);
    }
}
