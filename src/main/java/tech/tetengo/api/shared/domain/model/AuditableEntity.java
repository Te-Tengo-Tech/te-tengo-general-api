package tech.tetengo.api.shared.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Common identity and auditing. The id is a time-ordered UUID v7 generated in the constructor, never
 * by the database.
 *
 * <p>Because the id is assigned up front, {@link Persistable} tells Spring Data whether the entity is
 * new: new aggregates are persisted (not merged), so {@code save} keeps working on the same instance
 * and later changes to it are not lost.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @LastModifiedDate
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Transient
    private boolean nuevo = true;

    protected AuditableEntity() {
        this.id = UuidCreator.getTimeOrderedEpoch();
    }

    protected AuditableEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PostPersist
    void marcarComoGuardada() {
        nuevo = false;
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
