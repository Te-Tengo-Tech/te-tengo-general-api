package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * A family member's phone that receives push notices (API contract §7). Global: it belongs to a
 * user, who may be a member of several households. A push token belongs to one user at a time.
 */
@Entity
@Table(name = "dispositivos")
public class Dispositivo extends AggregateRoot {

    @Column(name = "token_push", nullable = false, updatable = false, length = 512)
    private String tokenPush;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Plataforma plataforma;

    protected Dispositivo() {}

    public Dispositivo(String tokenPush, UUID usuarioId, Plataforma plataforma) {
        this.tokenPush = Objects.requireNonNull(tokenPush, "tokenPush");
        asignar(usuarioId, plataforma);
    }

    /** The phone now belongs to whoever registered it last (e.g. another account signed in). */
    public void asignar(UUID usuarioId, Plataforma plataforma) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.plataforma = Objects.requireNonNull(plataforma, "plataforma");
    }

    public String getTokenPush() {
        return tokenPush;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Plataforma getPlataforma() {
        return plataforma;
    }
}
