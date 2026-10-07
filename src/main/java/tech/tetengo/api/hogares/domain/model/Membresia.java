package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;
import tech.tetengo.api.shared.domain.model.Rol;

/** A user's membership in a household, as {@code TITULAR} or {@code INVITADO}. Global table. */
@Entity
@Table(name = "membresias")
public class Membresia extends AggregateRoot {

    @Column(name = "hogar_id", nullable = false, updatable = false)
    private UUID hogarId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    protected Membresia() {}

    public Membresia(UUID hogarId, UUID usuarioId, Rol rol) {
        if (rol != Rol.TITULAR && rol != Rol.INVITADO) {
            throw new IllegalArgumentException("Rol de hogar inválido: " + rol);
        }
        this.hogarId = Objects.requireNonNull(hogarId, "hogarId");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.rol = rol;
    }

    public UUID getHogarId() {
        return hogarId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean esTitular() {
        return rol == Rol.TITULAR;
    }
}
