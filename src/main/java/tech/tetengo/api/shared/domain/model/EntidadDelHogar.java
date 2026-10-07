package tech.tetengo.api.shared.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base de todo dato que pertenece a un hogar (el tenant): cámaras, alertas, eventos, consentimientos…
 *
 * <p>{@link TenantId} hace que Hibernate rellene {@code hogar_id} al guardar con el hogar del contexto
 * y que filtre cada consulta por ese hogar. Ningún caso de uso debe escribir el {@code WHERE hogar_id}
 * a mano. Ver {@code docs/MULTITENANCY.md}.
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
