package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * A family member's phone or browser (the PWA) that receives push notices (API contract §7).
 * Global: it belongs to a user, who may be a member of several households. A push token belongs to
 * one user at a time.
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

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "referencia_push", length = 1024)
    private String referenciaPush;

    /** Last registration: the app registers its token every time it starts or comes back. */
    @Column(name = "visto_en", nullable = false)
    private Instant vistoEn;

    /** When the push service rejected the token; null while active. */
    @Column(name = "desactivado_en")
    private Instant desactivadoEn;

    protected Dispositivo() {}

    public Dispositivo(String tokenPush, UUID usuarioId, Plataforma plataforma, Instant ahora) {
        this.tokenPush = Objects.requireNonNull(tokenPush, "tokenPush");
        asignar(usuarioId, plataforma, ahora);
    }

    /**
     * The phone now belongs to whoever registered it last (e.g. another account signed in). A
     * registration also reactivates the device and records when it was last seen, even when nothing
     * else changed; the provider's address is dropped when the device was inactive or changed
     * platform, so the provider creates or re-enables it on the next push.
     */
    public void asignar(UUID usuarioId, Plataforma plataforma, Instant ahora) {
        Objects.requireNonNull(plataforma, "plataforma");
        if (!activo || (this.plataforma != null && this.plataforma != plataforma)) {
            referenciaPush = null;
        }
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.plataforma = plataforma;
        this.activo = true;
        this.desactivadoEn = null;
        this.vistoEn = Objects.requireNonNull(ahora, "ahora");
    }

    /**
     * The push service says the token is gone ({@code UNREGISTERED}, {@code SENDER_ID_MISMATCH}):
     * no more notices until the phone registers a token again.
     */
    public void desactivar(Instant ahora) {
        if (activo) {
            activo = false;
            desactivadoEn = ahora;
        }
    }

    /** The push provider's address of this device (the Amazon SNS platform endpoint ARN). */
    public void asignarReferenciaPush(String referencia) {
        this.referenciaPush = referencia;
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

    public boolean isActivo() {
        return activo;
    }

    public String getReferenciaPush() {
        return referenciaPush;
    }

    public Instant getVistoEn() {
        return vistoEn;
    }

    public Instant getDesactivadoEn() {
        return desactivadoEn;
    }
}
