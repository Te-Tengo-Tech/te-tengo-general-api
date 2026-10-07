package tech.tetengo.api.cuentas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/** US-03: a password reset link. It is valid for 30 minutes (CA-03.3) and can be used once. */
@Entity
@Table(name = "recuperaciones")
public class Recuperacion extends AggregateRoot {

    public static final Duration VIGENCIA = Duration.ofMinutes(30);

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "usada_en")
    private Instant usadaEn;

    protected Recuperacion() {}

    public Recuperacion(UUID usuarioId, String tokenHash, Instant ahora) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiraEn = ahora.plus(VIGENCIA);
    }

    /** CA-03.3: an expired or already used link is rejected; the user can ask for a new one. */
    public void usar(Instant ahora) {
        if (usadaEn != null || !ahora.isBefore(expiraEn)) {
            throw new ErrorDeNegocio(CuentaError.ENLACE_VENCIDO);
        }
        usadaEn = ahora;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadaEn() {
        return usadaEn;
    }
}
