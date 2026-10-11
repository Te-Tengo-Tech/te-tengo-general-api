package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * CA-28.6 (RFC 8628 §5.1): wrong link codes typed from one account. Like the sign-in lockout
 * (CA-02.3), 5 wrong codes in a row block code entry for 15 minutes; a code that exists resets the
 * count. Global table: one row per account.
 */
@Entity
@Table(name = "intentos_de_vinculacion")
public class IntentosDeVinculacion extends AggregateRoot {

    public static final int FALLOS_PERMITIDOS = 5;
    public static final Duration DURACION_DEL_BLOQUEO = Duration.ofMinutes(15);

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private int fallidos;

    @Column(name = "bloqueada_hasta")
    private Instant bloqueadaHasta;

    protected IntentosDeVinculacion() {}

    public IntentosDeVinculacion(UUID usuarioId) {
        this.usuarioId = Objects.requireNonNull(usuarioId);
    }

    public boolean bloqueada(Instant ahora) {
        return bloqueadaHasta != null && ahora.isBefore(bloqueadaHasta);
    }

    /** A wrong code; the fifth in a row blocks code entry. Returns the codes left before the block. */
    public int registrarFallo(Instant ahora) {
        if (bloqueadaHasta != null && !ahora.isBefore(bloqueadaHasta)) {
            bloqueadaHasta = null;
            fallidos = 0;
        }
        fallidos++;
        if (fallidos >= FALLOS_PERMITIDOS) {
            bloqueadaHasta = ahora.plus(DURACION_DEL_BLOQUEO);
            fallidos = 0;
            return 0;
        }
        return FALLOS_PERMITIDOS - fallidos;
    }

    /** A code that exists (valid, expired or used) ends the series of wrong ones. */
    public void reiniciar() {
        fallidos = 0;
        bloqueadaHasta = null;
    }

    public Instant getBloqueadaHasta() {
        return bloqueadaHasta;
    }

    public int getFallidos() {
        return fallidos;
    }
}
