package tech.tetengo.api.cuentas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;
import tech.tetengo.api.shared.domain.model.Rol;

/**
 * A signed-in session (US-02). It holds the hash of its refresh token, which rotates on every
 * refresh, and the household the access tokens are issued for. Closing it forces a new sign-in
 * (CA-02.4).
 */
@Entity
@Table(name = "sesiones")
public class SesionDeUsuario extends AggregateRoot {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "hogar_id")
    private UUID hogarId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Rol rol;

    @Column(name = "token_refresco_hash", nullable = false, length = 64)
    private String tokenRefrescoHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    protected SesionDeUsuario() {}

    public SesionDeUsuario(UUID usuarioId, UUID hogarId, Rol rol, String tokenRefrescoHash, Instant expiraEn) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        cambiarHogar(hogarId, rol);
        rotar(tokenRefrescoHash, expiraEn);
    }

    public boolean vigente(Instant ahora) {
        return cerradaEn == null && ahora.isBefore(expiraEn);
    }

    /** Every refresh replaces the refresh token, so a stolen old one stops working. */
    public void rotar(String nuevoTokenRefrescoHash, Instant nuevaExpiracion) {
        this.tokenRefrescoHash = Objects.requireNonNull(nuevoTokenRefrescoHash, "tokenRefrescoHash");
        this.expiraEn = Objects.requireNonNull(nuevaExpiracion, "expiraEn");
    }

    public void cambiarHogar(UUID hogarId, Rol rol) {
        if ((hogarId == null) != (rol == null)) {
            throw new IllegalArgumentException("El hogar y el rol van juntos");
        }
        this.hogarId = hogarId;
        this.rol = rol;
    }

    public void cerrar(Instant ahora) {
        if (cerradaEn == null) {
            cerradaEn = ahora;
        }
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getHogarId() {
        return hogarId;
    }

    public Rol getRol() {
        return rol;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }
}
