package tech.tetengo.api.shared.infrastructure.multitenancy;

import static org.hibernate.cfg.MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER;

import java.util.Map;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which household the session belongs to. With no household in context it returns
 * {@link #SIN_HOGAR}: household queries return nothing instead of every household's data (fail
 * closed).
 */
@Component
public class ResolvedorDeHogar implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    /** Non-existent household used when the request has none. */
    public static final UUID SIN_HOGAR = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return HogarActual.obtener().orElse(SIN_HOGAR);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> propiedades) {
        propiedades.put(MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
