package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.shared.infrastructure.almacenamiento.ProveedorDeAlmacenamiento;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.CuentaDeAzureBlob;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.PropiedadesDeAzureBlob;
import tech.tetengo.api.support.Azurite;

/**
 * Azure Blob with {@code tetengo.almacenamiento.proveedor=azure}; otherwise the S3 adapter when a
 * bucket is configured, or the in-memory fake.
 */
class AlmacenamientoDeClipsConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({PropiedadesDeS3.class, PropiedadesDeAzureBlob.class})
    static class Soporte {
        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }

        @Bean
        @Conditional(ProveedorDeAlmacenamiento.ConAzure.class)
        CuentaDeAzureBlob cuentaDeAzureBlob(PropiedadesDeAzureBlob propiedades, Clock reloj) {
            return CuentaDeAzureBlob.crear(propiedades, reloj);
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

    /**
     * Azure wins over a configured bucket. With the account key the URLs are service SAS of the
     * container, signed locally (no request), and the upload instructions carry {@code x-ms-blob-type}.
     */
    @Test
    void conProveedorAzureYClaveSeUsaAzureBlobConSas() {
        contexto.withPropertyValues(
                        "tetengo.almacenamiento.proveedor=azure",
                        "tetengo.almacenamiento.azure.cuenta=tetengoproddata",
                        "tetengo.almacenamiento.azure.clave=" + Azurite.CLAVE,
                        "tetengo.clips.azure.contenedor=clips",
                        "tetengo.clips.s3.bucket=te-tengo-clips")
                .run(ctx -> {
                    var almacenamiento = ctx.getBean(AlmacenamientoDeClips.class);
                    assertThat(almacenamiento).isInstanceOf(AlmacenamientoDeClipsEnAzure.class);
                    var subida = almacenamiento.urlDeSubida(
                            "hogares/h/alertas/a/e", "video/mp4", Instant.now().plusSeconds(600));
                    assertThat(subida.url().toString())
                            .startsWith("https://tetengoproddata.blob.core.windows.net/clips/hogares/h/alertas/a/e?")
                            .contains("sp=cw", "spr=https&", "sr=b", "sig=");
                    assertThat(subida.cabeceras())
                            .containsOnly(
                                    Map.entry("Content-Type", "video/mp4"), Map.entry("x-ms-blob-type", "BlockBlob"));

                    var descarga = almacenamiento
                            .urlDeLectura("hogares/h/alertas/a/e", Instant.now().plusSeconds(300), true, "clip")
                            .toString();
                    assertThat(descarga).contains("sp=r", "rscd=attachment");
                });
    }

    /** Production: no key, so Microsoft Entra ID (managed identity); nothing is requested at startup. */
    @Test
    void conProveedorAzureSinClaveSeUsaLaIdentidadAdministrada() {
        contexto.withPropertyValues(
                        "tetengo.almacenamiento.proveedor=azure",
                        "tetengo.almacenamiento.azure.endpoint=https://tetengoproddata.blob.core.windows.net")
                .run(ctx -> assertThat(ctx.getBean(AlmacenamientoDeClips.class))
                        .isInstanceOf(AlmacenamientoDeClipsEnAzure.class));
    }

    @Test
    void conProveedorAzureSinCuentaNiEndpointNoArranca() {
        contexto.withPropertyValues("tetengo.almacenamiento.proveedor=azure")
                .run(ctx -> assertThat(ctx).hasFailed());
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

    /**
     * Cloudflare R2 (developers.cloudflare.com/r2/examples/aws/aws-sdk-java/): region {@code auto},
     * the account's S3 endpoint and path-style URLs. Presigning needs no request: the URLs sign only
     * {@code Content-Type} and {@code Host}, with no SDK checksum parameters, so the agent's plain
     * PUT matches the signature.
     */
    @Test
    void conR2LasUrlsFirmadasUsanLaRegionAutoYNoLlevanChecksums() {
        String endpoint = "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com";
        contexto.withPropertyValues(
                        "tetengo.clips.s3.bucket=te-tengo-clips",
                        "tetengo.clips.s3.region=auto",
                        "tetengo.clips.s3.endpoint=" + endpoint,
                        "tetengo.clips.s3.path-style=true",
                        "tetengo.clips.s3.access-key=r2-access-key",
                        "tetengo.clips.s3.secret-key=r2-secret-key")
                .run(ctx -> {
                    var almacenamiento = ctx.getBean(AlmacenamientoDeClips.class);
                    var subida = almacenamiento.urlDeSubida(
                            "hogares/h/alertas/a/e", "video/mp4", Instant.now().plusSeconds(600));
                    String url = subida.url().toString();
                    assertThat(url)
                            .startsWith(endpoint + "/te-tengo-clips/hogares/h/alertas/a/e?")
                            .contains("X-Amz-Credential=r2-access-key%2F", "%2Fauto%2Fs3%2Faws4_request")
                            .contains("X-Amz-SignedHeaders=content-type%3Bhost")
                            .contains("X-Amz-Expires=")
                            .doesNotContainIgnoringCase("checksum");
                    assertThat(subida.cabeceras()).containsExactly(Map.entry("Content-Type", "video/mp4"));

                    var lectura = almacenamiento
                            .urlDeLectura("hogares/h/alertas/a/e", Instant.now().plusSeconds(300), false, "clip")
                            .toString();
                    assertThat(lectura)
                            .startsWith(endpoint + "/te-tengo-clips/hogares/h/alertas/a/e?")
                            .contains("X-Amz-SignedHeaders=host")
                            .doesNotContainIgnoringCase("checksum");
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
