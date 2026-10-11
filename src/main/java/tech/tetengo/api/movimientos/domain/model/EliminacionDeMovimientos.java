package tech.tetengo.api.movimientos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/** CA-35.4: the cloud movements must be deleted because the consent to store them was revoked. */
@Entity
@Table(name = "eliminaciones_de_movimientos")
public class EliminacionDeMovimientos extends EntidadDelHogar {

    @Column(name = "programada_en", nullable = false, updatable = false)
    private Instant programadaEn;

    @Column(name = "terminada_en")
    private Instant terminadaEn;

    @Column(name = "archivos_eliminados")
    private Long archivosEliminados;

    protected EliminacionDeMovimientos() {}

    public EliminacionDeMovimientos(Instant programadaEn) {
        this.programadaEn = Objects.requireNonNull(programadaEn);
    }

    /** Returns false if it was already done. */
    public boolean terminar(Instant ahora, long archivos) {
        if (terminadaEn != null) {
            return false;
        }
        terminadaEn = ahora;
        archivosEliminados = archivos;
        return true;
    }

    public Instant getProgramadaEn() {
        return programadaEn;
    }

    public Instant getTerminadaEn() {
        return terminadaEn;
    }

    public Long getArchivosEliminados() {
        return archivosEliminados;
    }
}
