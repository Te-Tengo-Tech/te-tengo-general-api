package tech.tetengo.api.shared.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Identidad y auditoría comunes. El identificador es un UUID v7 (ordenable por tiempo) generado en el
 * constructor, nunca por la base de datos.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @LastModifiedDate
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected AuditableEntity() {
        this.id = UuidCreator.getTimeOrderedEpoch();
    }

    protected AuditableEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    @Override
    public boolean equals(Object otro) {
        return this == otro || (otro instanceof AuditableEntity e && getClass() == e.getClass() && id.equals(e.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
