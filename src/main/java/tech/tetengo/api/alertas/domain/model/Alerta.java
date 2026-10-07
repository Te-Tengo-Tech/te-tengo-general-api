package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
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

    /** Object storage key of the clip, once the agent asked to upload it. */
    @Column(name = "clip_clave", length = 300)
    private String clipClave;

    /** The storage confirmed the upload. */
    @Column(name = "clip_subido", nullable = false)
    private boolean clipSubido;

    @Column(name = "clip_eliminado_en")
    private Instant clipEliminadoEn;

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

    /** CA-19.1: a family member attended it; the others see who and when (CA-19.3). */
    public void atender(UUID usuarioId, Instant ahora) {
        cerrar(EstadoAlerta.ATENDIDA, usuarioId, ahora);
    }

    /** CA-19.2: a false alarm; it is left out of the fall count. */
    public void marcarFalsaAlarma(UUID usuarioId, Instant ahora) {
        cerrar(EstadoAlerta.FALSA_ALARMA, usuarioId, ahora);
    }

    private void cerrar(EstadoAlerta nuevo, UUID usuarioId, Instant ahora) {
        if (estado != EstadoAlerta.ACTIVA) {
            throw new ErrorDeNegocio(AlertaError.ALERTA_CERRADA);
        }
        estado = nuevo;
        atendidaPor = Objects.requireNonNull(usuarioId, "usuarioId");
        atendidaEn = Objects.requireNonNull(ahora, "ahora");
    }

    /** The push was accepted by the push service (CA-16.1). */
    public void marcarNotificada(Instant instante) {
        if (notificadaEn == null) {
            notificadaEn = instante;
        }
    }

    /**
     * US-18: the agent uploads the clip of the event (6 s before and 6 s after, CA-18.1). Returns the
     * key it replaces, if any, so its object can be deleted.
     */
    public String asignarClip(String clave) {
        String anterior = clipEliminadoEn == null ? clipClave : null;
        clipClave = clave;
        clipSubido = false;
        clipEliminadoEn = null;
        return anterior;
    }

    public void confirmarClipSubido() {
        if (clipClave != null && clipEliminadoEn == null) {
            clipSubido = true;
        }
    }

    /** CA-09.1, CA-26.3: the recording was deleted (revocation or retention). */
    public void marcarClipEliminado(Instant instante) {
        if (clipClave != null && clipEliminadoEn == null) {
            clipEliminadoEn = instante;
            clipSubido = false;
        }
    }

    /** CA-18.2, CA-26.3: what the app can show about the clip. */
    public EstadoClip estadoClip() {
        if (clipEliminadoEn != null) {
            return EstadoClip.ELIMINADO;
        }
        return clipSubido ? EstadoClip.DISPONIBLE : EstadoClip.NO_DISPONIBLE;
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

    public String getClipClave() {
        return clipClave;
    }

    public boolean isClipSubido() {
        return clipSubido;
    }

    public Instant getClipEliminadoEn() {
        return clipEliminadoEn;
    }
}
