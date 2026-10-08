package tech.tetengo.api.shared.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tech.tetengo.api.shared.application.port.NotificadorPush;

/** {@code tetengo.push.proveedor} chooses the push adapter; the logging one is the default. */
class NotificadorPushConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
        PropiedadesDeFcm.class,
        PropiedadesDeSns.class,
        PropiedadesDelSimulador.class,
        PropiedadesDePushWeb.class
    })
    static class Soporte {}

    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(Soporte.class, NotificadorPushConfig.class);

    @Test
    void sinProveedorSoloSeRegistra() {
        contexto.run(
                ctx -> assertThat(ctx.getBean(NotificadorPush.class)).isInstanceOf(NotificadorPushEnRegistro.class));
    }

    @Test
    void fcmSinCredencialesNoArranca() {
        contexto.withPropertyValues("tetengo.push.proveedor=fcm")
                .run(ctx -> assertThat(ctx)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("TT_FCM_CREDENCIALES"));
    }

    @Test
    void snsConSusAplicacionesUsaAmazonSns() {
        contexto.withPropertyValues(
                        "tetengo.push.proveedor=sns",
                        "tetengo.push.sns.arn-android=arn:aws:sns:us-east-1:000000000000:app/GCM/android",
                        "tetengo.push.sns.arn-ios=arn:aws:sns:us-east-1:000000000000:app/GCM/ios",
                        "tetengo.push.sns.region=us-east-1",
                        "tetengo.push.sns.access-key=test",
                        "tetengo.push.sns.secret-key=test")
                .run(ctx -> assertThat(ctx.getBean(NotificadorPush.class)).isInstanceOf(NotificadorPushSns.class));
    }

    @Test
    void snsSinAplicacionesNoArranca() {
        contexto.withPropertyValues("tetengo.push.proveedor=sns", "tetengo.push.sns.region=us-east-1")
                .run(ctx ->
                        assertThat(ctx).hasFailed().getFailure().rootCause().hasMessageContaining("TT_SNS_ARN_IOS"));
    }

    @Test
    void unaUrlDeLaPwaQueNoEsHttpsNoArranca() {
        contexto.withPropertyValues("tetengo.push.web.enlace=http://te-tengo.test/app/")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause().hasMessageContaining("TT_PWA_URL"));
    }

    @Test
    void simuladorUsaElSimuladorDeIos() {
        contexto.withPropertyValues("tetengo.push.proveedor=simulador")
                .run(ctx ->
                        assertThat(ctx.getBean(NotificadorPush.class)).isInstanceOf(NotificadorPushSimulador.class));
    }

    @Test
    void unProveedorDesconocidoNoDejaAdaptador() {
        contexto.withPropertyValues("tetengo.push.proveedor=apns")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(NotificadorPush.class));
    }
}
