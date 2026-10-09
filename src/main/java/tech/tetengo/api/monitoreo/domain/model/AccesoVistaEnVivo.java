package tech.tetengo.api.monitoreo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * A live view session (US-23), which is also an entry of the access log (US-24): who watched, when it
 * started and how long it lasted (CA-24.1). Its viewer token (only the hash is stored) lets MediaMTX
 * serve the camera's stream while the session is open, at most until {@code expiraEn}. It ends when the
 * viewer closes it, when the viewer stops reading, at its maximum length, or when the camera stops
 * streaming (pause, consent revoked).
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

    /** Maximum end of the session. */
    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    /** First read authorized by MediaMTX. */
    @Column(name = "conectada_en")
    private Instant conectadaEn;

    /** Last time the viewer was seen reading. */
    @Column(name = "ultima_actividad")
    private Instant ultimaActividad;

    @Column
    private Instant fin;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    protected AccesoVistaEnVivo() {}

    public AccesoVistaEnVivo(
            UUID camaraId, UUID usuarioId, UUID alertaId, Instant inicio, Duration duracionMaxima, String tokenHash) {
        this.camaraId = Objects.requireNonNull(camaraId, "camaraId");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.alertaId = alertaId;
        this.inicio = Objects.requireNonNull(inicio, "inicio");
        this.expiraEn = inicio.plus(duracionMaxima);
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    }

    /** Open and within its maximum length: MediaMTX may serve the stream to its viewer. */
    public boolean admiteLectura(Instant ahora) {
        return fin == null && ahora.isBefore(expiraEn);
    }

    /** MediaMTX authorized a read with this session's token. */
    public void registrarLectura(Instant ahora) {
        if (conectadaEn == null) {
            conectadaEn = ahora;
        }
        registrarActividad(ahora);
    }

    /** The viewer was seen reading (an authorization or HLS traffic). */
    public void registrarActividad(Instant ahora) {
        if (fin == null && (ultimaActividad == null || ahora.isAfter(ultimaActividad))) {
            ultimaActividad = ahora;
        }
    }

    /**
     * When the session should have ended on its own by {@code ahora}, if it should: at the last time the
     * viewer was seen once it has been inactive for {@code inactividad} (at its start when it never
     * read), or at its maximum length.
     */
    public Optional<Instant> finPendiente(Instant ahora, Duration inactividad) {
        if (fin != null) {
            return Optional.empty();
        }
        Instant visto = ultimaActividad != null ? ultimaActividad : inicio;
        if (!ahora.isBefore(visto.plus(inactividad))) {
            return Optional.of(visto.isBefore(expiraEn) ? visto : expiraEn);
        }
        if (!ahora.isBefore(expiraEn)) {
            return Optional.of(expiraEn);
        }
        return Optional.empty();
    }

    /** CA-24.1: the session ended at {@code instante}; never before it started nor after its maximum. */
    public boolean finalizar(Instant instante) {
        if (fin != null) {
            return false;
        }
        Instant hasta = instante.isAfter(expiraEn) ? expiraEn : instante;
        fin = hasta.isBefore(inicio) ? inicio : hasta;
        return true;
    }

    public boolean abierta() {
        return fin == null;
    }

    /** CA-24.1: how long it lasted. An open session counts until now, up to its maximum length. */
    public long duracionSegundos(Instant ahora) {
        Instant hasta = fin != null ? fin : ahora.isAfter(expiraEn) ? expiraEn : ahora;
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

    public Instant getUltimaActividad() {
        return ultimaActividad;
    }

    public Instant getFin() {
        return fin;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}
