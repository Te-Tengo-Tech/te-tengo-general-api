package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-40 / CA-40.4: a member says there was a fall and the system did not warn. It is recorded as a
 * possible false negative for the evaluation; it creates no alert and notifies nobody. Any member may
 * send it (brief M-40.1).
 */
@Entity
@Table(name = "reportes_de_caida_sin_alerta")
public class ReporteDeCaidaSinAlerta extends EntidadDelHogar {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    /** The camera of the room, when the member chose one; null for «No sé». */
    @Column(name = "camara_id", updatable = false)
    private UUID camaraId;

    /** The room's name at the time, as the member chose it; null for «No sé». */
    @Column(updatable = false, length = 40)
    private String habitacion;

    @Column(name = "ocurrida_aproximadamente_en", nullable = false, updatable = false)
    private Instant ocurridaAproximadamenteEn;

    @Column(updatable = false, length = 300)
    private String nota;

    /** Whether movements of that moment could be stored (consent and cloud on), for the team. */
    @Column(name = "movimientos_disponibles", nullable = false, updatable = false)
    private boolean movimientosDisponibles;

    protected ReporteDeCaidaSinAlerta() {}

    public ReporteDeCaidaSinAlerta(
            UUID usuarioId,
            UUID camaraId,
            String habitacion,
            Instant ocurridaAproximadamenteEn,
            String nota,
            boolean movimientosDisponibles) {
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.camaraId = camaraId;
        this.habitacion = habitacion;
        this.ocurridaAproximadamenteEn = Objects.requireNonNull(ocurridaAproximadamenteEn);
        this.nota = nota == null || nota.isBlank() ? null : nota.strip();
        this.movimientosDisponibles = movimientosDisponibles;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public String getHabitacion() {
        return habitacion;
    }

    public Instant getOcurridaAproximadamenteEn() {
        return ocurridaAproximadamenteEn;
    }

    public String getNota() {
        return nota;
    }

    public boolean isMovimientosDisponibles() {
        return movimientosDisponibles;
    }
}
