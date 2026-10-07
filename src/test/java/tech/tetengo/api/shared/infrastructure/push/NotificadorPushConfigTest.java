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
    @EnableConfigurationProperties(PropiedadesDeFcm.class)
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
    void unProveedorDesconocidoNoDejaAdaptador() {
        contexto.withPropertyValues("tetengo.push.proveedor=apns")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(NotificadorPush.class));
    }
}
