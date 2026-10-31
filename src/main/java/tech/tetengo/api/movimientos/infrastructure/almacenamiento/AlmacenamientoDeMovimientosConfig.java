package tech.tetengo.api.movimientos.infrastructure.almacenamiento;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;
import tech.tetengo.api.shared.infrastructure.almacenamiento.ProveedorDeAlmacenamiento;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.CuentaDeAzureBlob;

/**
 * Azure Blob Storage with {@code tetengo.almacenamiento.proveedor=azure} (ADR 0013); otherwise S3 / R2
 * when {@code tetengo.movimientos.s3.bucket} is set, or the in-memory fake.
 */
@Configuration(proxyBeanMethods = false)
class AlmacenamientoDeMovimientosConfig {

    private static final String BUCKET = "tetengo.movimientos.s3.bucket";

    @Bean
    @Conditional(ProveedorDeAlmacenamiento.ConAzure.class)
    AlmacenamientoDeMovimientos almacenamientoDeMovimientosEnAzure(
            CuentaDeAzureBlob cuenta, @Value("${tetengo.movimientos.azure.contenedor:movements}") String contenedor) {
        return new AlmacenamientoDeMovimientosEnAzure(cuenta.contenedor(contenedor));
    }

    @Bean
    @Conditional(ConS3.class)
    AlmacenamientoDeMovimientos almacenamientoDeMovimientosEnS3(
            PropiedadesDeAlmacenamientoDeMovimientos propiedades, Clock reloj) {
        return AlmacenamientoDeMovimientosEnS3.crear(propiedades, reloj);
    }

    @Bean
    @Conditional(SinS3.class)
    AlmacenamientoDeMovimientos almacenamientoDeMovimientosEnMemoria() {
        return new AlmacenamientoDeMovimientosEnMemoria();
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
