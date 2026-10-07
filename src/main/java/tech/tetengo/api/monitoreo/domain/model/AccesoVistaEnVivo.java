package tech.tetengo.api.monitoreo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * A live view session (US-23), which is also an entry of the access log (US-24): who watched, when it
 * started and how long it lasted (CA-24.1). The stream URL works once, until {@code expiraEn}.
 */
@Entity
@Table(name = "accesos_vista_en_vivo")
public class AccesoVistaEnVivo extends EntidadDelHogar {

    @Column(name = "camara_id", nullable = false, updatable = false)
    private UUID camaraId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    /** CA-23.2: opened from an alert. */
    @Column(name = "alerta_id", updatable = false)
    private UUID alertaId;

    @Column(nullable = false, updatable = false)
    private Instant inicio;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "conectada_en")
    private Instant conectadaEn;

    @Column
    private Instant fin;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    protected AccesoVistaEnVivo() {}

    public AccesoVistaEnVivo(
            UUID camaraId, UUID usuarioId, UUID alertaId, Instant inicio, Duration vigencia, String tokenHash) {
        this.camaraId = Objects.requireNonNull(camaraId, "camaraId");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.alertaId = alertaId;
        this.inicio = Objects.requireNonNull(inicio, "inicio");
        this.expiraEn = inicio.plus(vigencia);
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    }

    /** The app opens the stream: once, before the URL expires and while the session is open. */
    public boolean conectar(Instant ahora) {
        if (conectadaEn != null || fin != null || !ahora.isBefore(expiraEn)) {
            return false;
        }
        conectadaEn = ahora;
        return true;
    }

    /** CA-24.1: the viewer closed the live view. */
    public void cerrar(Instant ahora) {
        if (fin == null) {
            fin = ahora;
        }
    }

    /**
     * CA-24.1: how long it lasted. An open session counts until now; one whose stream was never opened
     * ends when its URL expired.
     */
    public long duracionSegundos(Instant ahora) {
        Instant hasta = fin != null ? fin : conectadaEn == null && ahora.isAfter(expiraEn) ? expiraEn : ahora;
        return Math.max(0, Duration.between(inicio, hasta).toSeconds());
    }

    public boolean desdeAlerta() {
        return alertaId != null;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getAlertaId() {
        return alertaId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getConectadaEn() {
        return conectadaEn;
    }

    public Instant getFin() {
        return fin;
    }
}
