package tech.tetengo.api.shared.infrastructure.multitenancy;

import static org.hibernate.cfg.MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER;

import java.util.Map;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Le dice a Hibernate a qué hogar pertenece la sesión. Sin hogar en el contexto devuelve
 * {@link #SIN_HOGAR}: las consultas a datos del hogar no devuelven nada en lugar de devolver datos de
 * todos los hogares (falla cerrada).
 */
@Component
public class ResolvedorDeHogar implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    /** Hogar inexistente usado cuando la petición no tiene hogar. */
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
