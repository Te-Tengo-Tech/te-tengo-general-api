package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;

/** The S3 adapter is used only when a bucket is configured; otherwise the in-memory fake stays. */
class AlmacenamientoDeClipsConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropiedadesDeS3.class)
    static class Soporte {
        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }
    }

    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(Soporte.class, AlmacenamientoDeClipsConfig.class);

    @Test
    void sinBucketSeUsaElAlmacenamientoEnMemoria() {
        contexto.withPropertyValues("tetengo.clips.s3.bucket=")
                .run(ctx -> assertThat(ctx.getBean(AlmacenamientoDeClips.class))
                        .isInstanceOf(AlmacenamientoDeClipsEnMemoria.class));
    }

    @Test
    void conBucketSeUsaS3() {
        contexto.withPropertyValues(
                        "tetengo.clips.s3.bucket=te-tengo-clips",
                        "tetengo.clips.s3.region=us-east-1",
                        "tetengo.clips.s3.endpoint=http://localhost:4566",
                        "tetengo.clips.s3.path-style=true",
                        "tetengo.clips.s3.access-key=local",
                        "tetengo.clips.s3.secret-key=local")
                .run(ctx -> {
                    assertThat(ctx.getBean(AlmacenamientoDeClips.class)).isInstanceOf(AlmacenamientoDeClipsEnS3.class);
                    var subida = ctx.getBean(AlmacenamientoDeClips.class)
                            .urlDeSubida(
                                    "hogares/h/alertas/a/e",
                                    "video/mp4",
                                    Instant.now().plusSeconds(600));
                    assertThat(subida.url().toString()).startsWith("http://localhost:4566/te-tengo-clips/hogares/h/");
                });
    }

    @Test
    void elPerfilLocalUsaFlociEnLocalhost4566() throws Exception {
        var fuente = (EnumerablePropertySource<?>) new YamlPropertySourceLoader()
                .load("application-local.yml", new ClassPathResource("application-local.yml"))
                .getFirst();
        assertThat(fuente.getProperty("tetengo.clips.s3.crear-bucket")).isEqualTo(true);
        List<String> valores = new ArrayList<>();
        for (String nombre : fuente.getPropertyNames()) {
            valores.add(nombre + "=" + fuente.getProperty(nombre));
        }
        // Do not try to reach a real store from a unit test.
        valores.add("tetengo.clips.s3.crear-bucket=false");

        contexto.withPropertyValues(valores.toArray(String[]::new)).run(ctx -> {
            PropiedadesDeS3 propiedades = ctx.getBean(PropiedadesDeS3.class);
            assertThat(propiedades.bucket()).isEqualTo("te-tengo-clips");
            assertThat(propiedades.pathStyle()).isTrue();
            var subida = ctx.getBean(AlmacenamientoDeClips.class)
                    .urlDeSubida("clave", "video/mp4", Instant.now().plusSeconds(600));
            assertThat(subida.url().toString()).startsWith("http://localhost:4566/te-tengo-clips/clave");
        });
    }
}
