package tech.tetengo.api.movimientos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-39 / CA-39.3: a replay of a camera's movements, which is also an entry of the access log next to
 * live view (US-24): who watched, which camera and moment, when it started and how long it lasted.
 * It ends when the member closes it, at the latest after its maximum length.
 */
@Entity
@Table(name = "repeticiones_de_movimiento")
public class RepeticionDeMovimiento extends EntidadDelHogar {

    @Column(name = "camara_id", nullable = false, updatable = false)
    private UUID camaraId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "momento_desde", nullable = false)
    private Instant momentoDesde;

    @Column(name = "momento_hasta", nullable = false)
    private Instant momentoHasta;

    @Column(nullable = false, updatable = false)
    private Instant inicio;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column
    private Instant fin;

    protected RepeticionDeMovimiento() {}

    public RepeticionDeMovimiento(
            UUID camaraId, UUID usuarioId, Instant desde, Instant hasta, Instant inicio, Duration duracionMaxima) {
        this.camaraId = Objects.requireNonNull(camaraId);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.momentoDesde = desde;
        this.momentoHasta = hasta;
        this.inicio = inicio;
        this.expiraEn = inicio.plus(duracionMaxima);
    }

    /** Open, not past its maximum length, and its own member's. */
    public boolean abiertaPara(UUID usuario, Instant ahora) {
        return fin == null && ahora.isBefore(expiraEn) && usuarioId.equals(usuario);
    }

    /** The member moved along the timeline: the logged moment covers it too. */
    public void ampliar(Instant desde, Instant hasta) {
        if (desde.isBefore(momentoDesde)) {
            momentoDesde = desde;
        }
        if (hasta.isAfter(momentoHasta)) {
            momentoHasta = hasta;
        }
    }

    /** CA-39.3: the member closed it; never after its maximum length. */
    public boolean finalizar(Instant ahora) {
        if (fin != null) {
            return false;
        }
        fin = ahora.isAfter(expiraEn) ? expiraEn : ahora;
        return true;
    }

    /** How long it lasted; an open one counts until now, up to its maximum. */
    public long duracionSegundos(Instant ahora) {
        Instant hasta = fin != null ? fin : ahora.isAfter(expiraEn) ? expiraEn : ahora;
        return Math.max(0, Duration.between(inicio, hasta).toSeconds());
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getMomentoDesde() {
        return momentoDesde;
    }

    public Instant getMomentoHasta() {
        return momentoHasta;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }
}
