package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/** US-09: the household's recordings must be deleted because its consent was revoked (CA-09.1). */
@Entity
@Table(name = "eliminaciones_de_grabaciones")
public class EliminacionDeGrabaciones extends EntidadDelHogar {

    @Column(name = "solicitada_en", nullable = false, updatable = false)
    private Instant solicitadaEn;

    @Column(name = "completada_en")
    private Instant completadaEn;

    @Column(name = "clips_eliminados")
    private Long clipsEliminados;

    /** Movement files deleted with the recordings (US-37); null while pending. */
    @Column(name = "movimientos_eliminados")
    private Long movimientosEliminados;

    protected EliminacionDeGrabaciones() {}

    public EliminacionDeGrabaciones(Instant solicitadaEn) {
        this.solicitadaEn = Objects.requireNonNull(solicitadaEn, "solicitadaEn");
    }

    /** CA-09.3: done, after deleting {@code clips} recordings; returns false if it already was. */
    public boolean completar(Instant ahora, long clips) {
        return completar(ahora, clips, 0);
    }

    /** The same, also counting the movement files it deleted (US-37, CA-09.1). */
    public boolean completar(Instant ahora, long clips, long movimientos) {
        if (completadaEn != null) {
            return false;
        }
        completadaEn = ahora;
        clipsEliminados = clips;
        movimientosEliminados = movimientos;
        return true;
    }

    public Long getMovimientosEliminados() {
        return movimientosEliminados;
    }

    public Instant getSolicitadaEn() {
        return solicitadaEn;
    }

    public Instant getCompletadaEn() {
        return completadaEn;
    }

    /** Recordings this deletion removed; null while it is pending. */
    public Long getClipsEliminados() {
        return clipsEliminados;
    }
}
