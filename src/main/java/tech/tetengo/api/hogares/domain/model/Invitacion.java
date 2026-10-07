package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * US-08: an invitation e-mailed to another family member (CA-08.1). Accepting it links them to the
 * household as {@code INVITADO} (CA-08.2). It works once and until it expires.
 */
@Entity
@Table(name = "invitaciones")
public class Invitacion extends AggregateRoot {

    @Column(name = "hogar_id", nullable = false, updatable = false)
    private UUID hogarId;

    @Column(nullable = false, updatable = false, length = 254)
    private String correo;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "invitado_por", nullable = false, updatable = false)
    private UUID invitadoPor;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "aceptada_en")
    private Instant aceptadaEn;

    protected Invitacion() {}

    public Invitacion(
            UUID hogarId, String correo, String tokenHash, UUID invitadoPor, Instant ahora, Duration vigencia) {
        this.hogarId = Objects.requireNonNull(hogarId, "hogarId");
        this.correo = Objects.requireNonNull(correo, "correo").strip().toLowerCase(Locale.ROOT);
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.invitadoPor = Objects.requireNonNull(invitadoPor, "invitadoPor");
        this.expiraEn = ahora.plus(vigencia);
    }

    /** An expired or already accepted invitation answers {@code 410 INVITACION_VENCIDA}. */
    public void aceptar(Instant ahora) {
        if (aceptadaEn != null || !ahora.isBefore(expiraEn)) {
            throw new ErrorDeNegocio(HogarError.INVITACION_VENCIDA);
        }
        aceptadaEn = ahora;
    }

    public UUID getHogarId() {
        return hogarId;
    }

    public String getCorreo() {
        return correo;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getAceptadaEn() {
        return aceptadaEn;
    }
}
