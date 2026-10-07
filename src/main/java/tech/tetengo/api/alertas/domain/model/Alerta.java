package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * An alert of a fall (high severity) or an unstable movement (medium severity, CA-17.1), with the
 * room and time of the event. Created and updated from the household agent's events.
 */
@Entity
@Table(name = "alertas")
public class Alerta extends EntidadDelHogar {

    @Column(name = "camara_id", nullable = false, updatable = false)
    private UUID camaraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAlerta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severidad severidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAlerta estado;

    @Column(nullable = false)
    private boolean confirmada;

    @Column(name = "origen_inestable", nullable = false)
    private boolean origenInestable;

    /** Room name when the alert happened: renaming the camera only affects later alerts (CA-06.2). */
    @Column(nullable = false, updatable = false, length = 40)
    private String habitacion;

    @Column(name = "ocurrida_en", nullable = false, updatable = false)
    private Instant ocurridaEn;

    @Column(name = "notificada_en")
    private Instant notificadaEn;

    @Column(name = "recuperada_en")
    private Instant recuperadaEn;

    @Column(name = "atendida_por")
    private UUID atendidaPor;

    @Column(name = "atendida_en")
    private Instant atendidaEn;

    @Column(name = "escalada_en")
    private Instant escaladaEn;

    protected Alerta() {}

    private Alerta(TipoAlerta tipo, UUID camaraId, String habitacion, Instant ocurridaEn) {
        this.tipo = tipo;
        this.severidad = tipo.severidad();
        this.estado = EstadoAlerta.ACTIVA;
        this.camaraId = Objects.requireNonNull(camaraId, "camaraId");
        this.habitacion = Objects.requireNonNull(habitacion, "habitacion");
        this.ocurridaEn = Objects.requireNonNull(ocurridaEn, "ocurridaEn");
    }

    /** CA-11.1, CA-11.2: a fall with the room and the time. */
    public static Alerta caida(UUID camaraId, String habitacion, Instant ocurridaEn) {
        return new Alerta(TipoAlerta.CAIDA, camaraId, habitacion, ocurridaEn);
    }

    /** CA-14.1, CA-17.1: an unstable movement, medium severity. */
    public static Alerta movimientoInestable(UUID camaraId, String habitacion, Instant ocurridaEn) {
        return new Alerta(TipoAlerta.MOVIMIENTO_INESTABLE, camaraId, habitacion, ocurridaEn);
    }

    /** CA-14.3, CA-17.3: the unstable movement ended on the floor; the alert becomes a fall. */
    public boolean evolucionarACaida() {
        if (tipo != TipoAlerta.MOVIMIENTO_INESTABLE || estado != EstadoAlerta.ACTIVA) {
            return false;
        }
        tipo = TipoAlerta.CAIDA;
        severidad = TipoAlerta.CAIDA.severidad();
        origenInestable = true;
        return true;
    }

    /** CA-13.1: still on the floor after 30 s; the fall is confirmed and the alert stays active. */
    public boolean confirmar() {
        if (tipo != TipoAlerta.CAIDA || confirmada) {
            return false;
        }
        confirmada = true;
        return true;
    }

    /** CA-13.2: the person got up after the fall. */
    public boolean registrarRecuperacion(Instant instante) {
        if (tipo != TipoAlerta.CAIDA || recuperadaEn != null) {
            return false;
        }
        recuperadaEn = instante;
        return true;
    }

    /** The push was accepted by the push service (CA-16.1). */
    public void marcarNotificada(Instant instante) {
        if (notificadaEn == null) {
            notificadaEn = instante;
        }
    }

    public boolean activa() {
        return estado == EstadoAlerta.ACTIVA;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public TipoAlerta getTipo() {
        return tipo;
    }

    public Severidad getSeveridad() {
        return severidad;
    }

    public EstadoAlerta getEstado() {
        return estado;
    }

    public boolean isConfirmada() {
        return confirmada;
    }

    public boolean isOrigenInestable() {
        return origenInestable;
    }

    public String getHabitacion() {
        return habitacion;
    }

    public Instant getOcurridaEn() {
        return ocurridaEn;
    }

    public Instant getNotificadaEn() {
        return notificadaEn;
    }

    public Instant getRecuperadaEn() {
        return recuperadaEn;
    }

    public UUID getAtendidaPor() {
        return atendidaPor;
    }

    public Instant getAtendidaEn() {
        return atendidaEn;
    }

    public Instant getEscaladaEn() {
        return escaladaEn;
    }
}
