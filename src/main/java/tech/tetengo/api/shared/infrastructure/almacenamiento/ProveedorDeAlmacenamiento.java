package tech.tetengo.api.shared.infrastructure.almacenamiento;

import java.util.Locale;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Object store of the clips, the movement files and the photos, chosen by {@code
 * tetengo.almacenamiento.proveedor} (ADR 0013): {@code s3} (default; Amazon S3 or an S3-compatible
 * store such as Cloudflare R2, or the in-memory fakes without a bucket) or {@code azure} (Azure Blob
 * Storage, {@code tetengo.almacenamiento.azure.*}).
 */
public enum ProveedorDeAlmacenamiento {
    S3,
    AZURE;

    public static final String PROPIEDAD = "tetengo.almacenamiento.proveedor";

    /** The configured provider; blank is {@code s3}, an unknown value stops the application. */
    public static ProveedorDeAlmacenamiento de(Environment entorno) {
        String valor = entorno.getProperty(PROPIEDAD);
        if (!StringUtils.hasText(valor)) {
            return S3;
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "%s=%s no es un proveedor de almacenamiento: s3 o azure".formatted(PROPIEDAD, valor), e);
        }
    }

    public static boolean esAzure(Environment entorno) {
        return de(entorno) == AZURE;
    }

    /** Matches when the provider is {@code azure}. */
    public static class ConAzure implements Condition {
        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadatos) {
            return esAzure(contexto.getEnvironment());
        }
    }
}
