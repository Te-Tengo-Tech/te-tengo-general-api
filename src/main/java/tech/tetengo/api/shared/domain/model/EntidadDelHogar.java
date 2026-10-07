package tech.tetengo.api.shared.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base for all data that belongs to a household (the tenant): cameras, alerts, events, consents…
 *
 * <p>{@link TenantId} makes Hibernate fill {@code hogar_id} on save with the household in context and
 * filter every query by it. No use case may write {@code WHERE hogar_id} by hand. See
 * {@code docs/MULTITENANCY.md}.
 */
@MappedSuperclass
public abstract class EntidadDelHogar extends AggregateRoot {

    @TenantId
    @Column(name = "hogar_id", nullable = false, updatable = false)
    private UUID hogarId;

    protected EntidadDelHogar() {
        super();
    }

    public UUID getHogarId() {
        return hogarId;
    }
}
