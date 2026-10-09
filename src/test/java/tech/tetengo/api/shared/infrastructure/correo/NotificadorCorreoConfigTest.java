package tech.tetengo.api.shared.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/** {@code tetengo.correo.proveedor} chooses the e-mail adapter; the logging one is the default. */
class NotificadorCorreoConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({PropiedadesDeSes.class, PropiedadesDeSmtp.class})
    static class Soporte {}

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(Soporte.class, NotificadorCorreoConfig.class);

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
    void sinProveedorSmtpNoSeCreaElEnviadorDeCorreo() {
        contexto.run(ctx -> assertThat(ctx).doesNotHaveBean(JavaMailSender.class));
        contexto.withPropertyValues(
                        "tetengo.correo.proveedor=ses",
                        "tetengo.correo.ses.remitente=no-responder@tetengo.test",
                        "tetengo.correo.ses.region=us-east-1",
                        "tetengo.correo.ses.endpoint=http://localhost:4566",
                        "tetengo.correo.ses.access-key=test",
                        "tetengo.correo.ses.secret-key=test")
                .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(JavaMailSender.class));
    }

    @Test
    void conSmtpSeUsaElServidorDeSpringMail() {
        contexto.withPropertyValues(
                        "tetengo.correo.proveedor=smtp",
                        "tetengo.correo.smtp.remitente=Te Tengo <no-responder@tetengo.test>",
                        "spring.mail.host=localhost",
                        "spring.mail.port=1025")
                .run(ctx ->
                        assertThat(ctx.getBean(NotificadorCorreo.class)).isInstanceOf(NotificadorCorreoEnSmtp.class));
    }

    @Test
    void smtpSinServidorNoArranca() {
        contexto.withPropertyValues(
                        "tetengo.correo.proveedor=smtp", "tetengo.correo.smtp.remitente=no-responder@tetengo.test")
                .run(ctx -> assertThat(ctx)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("spring.mail.host (SPRING_MAIL_HOST) es obligatorio con SMTP"));
    }

    @Test
    void smtpSinRemitenteNoArranca() {
        contexto.withPropertyValues("tetengo.correo.proveedor=smtp", "spring.mail.host=localhost")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void unProveedorDesconocidoNoDejaAdaptador() {
        contexto.withPropertyValues("tetengo.correo.proveedor=otro")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(NotificadorCorreo.class));
    }

    @Test
    void conSmtpLaConfiguracionPorDefectoExigeStarttls() throws Exception {
        var fuente = (EnumerablePropertySource<?>) new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .getFirst();
        List<String> valores = new ArrayList<>();
        for (String nombre : fuente.getPropertyNames()) {
            if (nombre.startsWith("spring.mail.") || nombre.startsWith("tetengo.correo.")) {
                valores.add(nombre + "=" + fuente.getProperty(nombre));
            }
        }
        valores.add("tetengo.correo.proveedor=smtp");
        valores.add("tetengo.correo.smtp.remitente=no-responder@tetengo.test");
        valores.add("spring.mail.host=smtp.example.com");
        valores.add("spring.mail.port=587");

        contexto.withPropertyValues(valores.toArray(String[]::new)).run(ctx -> {
            var propiedades = ((JavaMailSenderImpl) ctx.getBean(JavaMailSender.class)).getJavaMailProperties();
            assertThat(propiedades)
                    .containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .containsKeys("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout");
            assertThat(ctx.getBean(NotificadorCorreo.class)).isInstanceOf(NotificadorCorreoEnSmtp.class);
        });
    }
}
