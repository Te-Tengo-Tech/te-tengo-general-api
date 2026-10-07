package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;

/**
 * Chooses the clip storage: Amazon S3 (or an S3-compatible store) when {@code tetengo.clips.s3.bucket}
 * is set, otherwise the in-memory fake.
 */
@Configuration(proxyBeanMethods = false)
class AlmacenamientoDeClipsConfig {

    private static final String BUCKET = "tetengo.clips.s3.bucket";

    @Bean
    @Conditional(ConS3.class)
    AlmacenamientoDeClips almacenamientoDeClipsEnS3(PropiedadesDeS3 propiedades, Clock reloj) {
        return AlmacenamientoDeClipsEnS3.crear(propiedades, reloj);
    }

    @Bean
    @Conditional(SinS3.class)
    AlmacenamientoDeClips almacenamientoDeClipsEnMemoria() {
        return new AlmacenamientoDeClipsEnMemoria();
    }

    static class ConS3 implements Condition {
        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadatos) {
            return StringUtils.hasText(contexto.getEnvironment().getProperty(BUCKET));
        }
    }

    static class SinS3 implements Condition {
        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadatos) {
            return !StringUtils.hasText(contexto.getEnvironment().getProperty(BUCKET));
        }
    }
}
