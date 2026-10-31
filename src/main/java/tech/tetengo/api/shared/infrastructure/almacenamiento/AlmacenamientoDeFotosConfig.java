package tech.tetengo.api.shared.infrastructure.almacenamiento;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.CuentaDeAzureBlob;

/**
 * Azure Blob Storage with {@code tetengo.almacenamiento.proveedor=azure} (ADR 0013); otherwise S3 / R2
 * when {@code tetengo.fotos.s3.bucket} is set, or the in-memory fake.
 */
@Configuration(proxyBeanMethods = false)
class AlmacenamientoDeFotosConfig {

    private static final String BUCKET = "tetengo.fotos.s3.bucket";

    @Bean
    @Conditional(ProveedorDeAlmacenamiento.ConAzure.class)
    AlmacenamientoDeFotos almacenamientoDeFotosEnAzure(
            CuentaDeAzureBlob cuenta, @Value("${tetengo.fotos.azure.contenedor:clips}") String contenedor) {
        return new AlmacenamientoDeFotosEnAzure(cuenta.contenedor(contenedor));
    }

    @Bean
    @Conditional(ConS3.class)
    AlmacenamientoDeFotos almacenamientoDeFotosEnS3(PropiedadesDeAlmacenamientoDeFotos propiedades, Clock reloj) {
        return AlmacenamientoDeFotosEnS3.crear(propiedades, reloj);
    }

    @Bean
    @Conditional(SinS3.class)
    AlmacenamientoDeFotos almacenamientoDeFotosEnMemoria() {
        return new AlmacenamientoDeFotosEnMemoria();
    }

    static class ConS3 implements Condition {
        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadatos) {
            return !ProveedorDeAlmacenamiento.esAzure(contexto.getEnvironment())
                    && StringUtils.hasText(contexto.getEnvironment().getProperty(BUCKET));
        }
    }

    static class SinS3 implements Condition {
        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadatos) {
            return !ProveedorDeAlmacenamiento.esAzure(contexto.getEnvironment())
                    && !StringUtils.hasText(contexto.getEnvironment().getProperty(BUCKET));
        }
    }
}
