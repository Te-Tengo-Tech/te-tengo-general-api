package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * A refresh token of a PC (RFC 6749 §6). The PC is a public client, so refresh tokens rotate (RFC 9700
 * §2.2.2, §4.14.2): each one works once and its use issues the next. A rotated token presented again
 * means someone else may hold the PC's credentials, so every token of the PC is revoked. Each token
 * expires 90 days after it was issued (CA-28.7). Only the SHA-256 is stored. Global table: the token
 * request carries no JWT.
 */
@Entity
@Table(name = "tokens_de_renovacion_de_pc")
public class TokenDeRenovacionDePc extends AggregateRoot {

    @Column(name = "pc_id", nullable = false, updatable = false)
    private UUID pcId;

    @Column(name = "hogar_id", nullable = false, updatable = false)
    private UUID hogarId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false, length = 60)
    private String cliente;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    /** When it was rotated: it can never be used again. */
    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    protected TokenDeRenovacionDePc() {}

    public TokenDeRenovacionDePc(UUID pcId, UUID hogarId, String tokenHash, String cliente, Instant expiraEn) {
        this.pcId = Objects.requireNonNull(pcId);
        this.hogarId = Objects.requireNonNull(hogarId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.cliente = Objects.requireNonNull(cliente);
        this.expiraEn = Objects.requireNonNull(expiraEn);
    }

    /** What presenting this token means (RFC 9700 §4.14.2). */
    public enum Uso {
        VALIDO,
        /** Already rotated: possible theft; revoke the whole PC. */
        REUTILIZADO,
        REVOCADO,
        VENCIDO,
        OTRO_CLIENTE
    }

    public Uso evaluar(String clientePresentado, Instant ahora) {
        if (revocadoEn != null) {
            return Uso.REVOCADO;
        }
        if (usadoEn != null) {
            return Uso.REUTILIZADO;
        }
        if (!cliente.equals(clientePresentado)) {
            return Uso.OTRO_CLIENTE;
        }
        if (!ahora.isBefore(expiraEn)) {
            return Uso.VENCIDO;
        }
        return Uso.VALIDO;
    }

    /** Rotation: this token was traded for the next one. */
    public void usar(Instant ahora) {
        if (usadoEn == null) {
            usadoEn = ahora;
        }
    }

    public void revocar(Instant ahora) {
        if (revocadoEn == null) {
            revocadoEn = ahora;
        }
    }

    public String getCliente() {
        return cliente;
    }

    public UUID getPcId() {
        return pcId;
    }

    public UUID getHogarId() {
        return hogarId;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }
}
