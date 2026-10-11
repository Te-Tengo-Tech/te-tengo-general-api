package tech.tetengo.api.movimientos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * CA-37.2 «el backend guarda solo un índice del archivo y un resumen»: per camera and day of the
 * household (America/Lima), how many files, frames and seconds were stored. Counts only, no landmarks;
 * the evaluation (US-40) may add more fields. Deleted with the movements.
 */
@Entity
@Table(name = "resumenes_de_movimiento")
public class ResumenDiarioDeMovimiento extends EntidadDelHogar {

    @Column(name = "camara_id", nullable = false, updatable = false)
    private UUID camaraId;

    @Column(nullable = false, updatable = false)
    private LocalDate dia;

    @Column(nullable = false)
    private int archivos;

    @Column(nullable = false)
    private long fotogramas;

    @Column(nullable = false)
    private long segundos;

    protected ResumenDiarioDeMovimiento() {}

    public ResumenDiarioDeMovimiento(UUID camaraId, LocalDate dia) {
        this.camaraId = Objects.requireNonNull(camaraId);
        this.dia = Objects.requireNonNull(dia);
    }

    public void sumar(ArchivoDeMovimiento archivo) {
        archivos++;
        fotogramas += archivo.getFotogramas();
        segundos += Duration.between(archivo.getDesde(), archivo.getHasta()).toSeconds();
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public LocalDate getDia() {
        return dia;
    }

    public int getArchivos() {
        return archivos;
    }

    public long getFotogramas() {
        return fotogramas;
    }

    public long getSegundos() {
        return segundos;
    }
}
