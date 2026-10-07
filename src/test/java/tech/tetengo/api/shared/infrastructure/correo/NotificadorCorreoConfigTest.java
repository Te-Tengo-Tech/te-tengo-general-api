package tech.tetengo.api.shared.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/** {@code tetengo.correo.proveedor} chooses the e-mail adapter; the logging one is the default. */
class NotificadorCorreoConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropiedadesDeSes.class)
    static class Soporte {}

    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(Soporte.class, NotificadorCorreoConfig.class);

    @Test
    void sinProveedorSoloSeRegistra() {
        contexto.run(ctx ->
                assertThat(ctx.getBean(NotificadorCorreo.class)).isInstanceOf(NotificadorCorreoEnRegistro.class));
    }

    @Test
    void conSesSeUsaAmazonSes() {
        contexto.withPropertyValues(
                        "tetengo.correo.proveedor=ses",
                        "tetengo.correo.ses.remitente=no-responder@tetengo.test",
                        "tetengo.correo.ses.region=us-east-1",
                        "tetengo.correo.ses.endpoint=http://localhost:4566",
                        "tetengo.correo.ses.access-key=test",
                        "tetengo.correo.ses.secret-key=test")
                .run(ctx ->
                        assertThat(ctx.getBean(NotificadorCorreo.class)).isInstanceOf(NotificadorCorreoEnSes.class));
    }

    @Test
    void sesSinRemitenteNoArranca() {
        contexto.withPropertyValues("tetengo.correo.proveedor=ses", "tetengo.correo.ses.region=us-east-1")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void unProveedorDesconocidoNoDejaAdaptador() {
        contexto.withPropertyValues("tetengo.correo.proveedor=smtp")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(NotificadorCorreo.class));
    }
}
