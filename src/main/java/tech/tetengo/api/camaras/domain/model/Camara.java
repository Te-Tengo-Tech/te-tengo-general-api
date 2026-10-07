package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import tech.tetengo.api.camaras.CamaraDesconectada;
import tech.tetengo.api.camaras.CamaraReconectada;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/** Camera installed in the household. Rules live here, not in controllers. */
@Entity
@Table(name = "camaras")
public class Camara extends EntidadDelHogar {

    static final int LARGO_MAXIMO_NOMBRE = 40;

    @Column(name = "nombre_habitacion", nullable = false, length = LARGO_MAXIMO_NOMBRE)
    private String nombreHabitacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conexion", nullable = false, length = 20)
    private EstadoConexion estadoConexion;

    @Column(name = "ultima_senal")
    private Instant ultimaSenal;

    @Column(name = "pausada_hasta")
    private Instant pausadaHasta;

    @Column(name = "deteccion_confiable", nullable = false)
    private boolean deteccionConfiable = true;

    protected Camara() {}

    public Camara(String nombreHabitacion) {
        renombrar(nombreHabitacion);
        this.estadoConexion = EstadoConexion.DESCONECTADA;
    }

    /** CA-06.2 and CA-06.3: the name cannot be empty. */
    public void renombrar(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ErrorDeNegocio(CamaraError.NOMBRE_VACIO);
        }
        if (nombre.strip().length() > LARGO_MAXIMO_NOMBRE) {
            throw new ErrorDeNegocio(CamaraError.NOMBRE_MUY_LARGO);
        }
        this.nombreHabitacion = nombre.strip();
    }

    /**
     * CA-07.1: the agent reports a heartbeat; the camera goes online with the time of the last signal.
     * A camera that had been online before and was disconnected is back (CA-07.3).
     */
    public void registrarSenal(Instant instante) {
        boolean reconexion = estadoConexion == EstadoConexion.DESCONECTADA && ultimaSenal != null;
        this.ultimaSenal = instante;
        this.estadoConexion = EstadoConexion.EN_LINEA;
        if (reconexion) {
            registrarEvento(new CamaraReconectada(getHogarId(), getId(), nombreHabitacion, instante));
        }
    }

    /**
     * CA-07.2: with no heartbeat since {@code limite}, the camera is disconnected. Returns whether it
     * changed, so running the job twice does nothing the second time.
     */
    public boolean desconectarSiNoHaySenalDesde(Instant limite, Instant ahora) {
        if (ultimaSenal == null || !ultimaSenal.isBefore(limite)) {
            return false;
        }
        return desconectar(ahora);
    }

    /**
     * CA-07.2: the agent reports that its webcam is not available, so the camera is disconnected right
     * away. Returns whether it changed: an already disconnected camera (or one that never came online)
     * sends no new notice.
     */
    public boolean desconectar(Instant ahora) {
        if (estadoConexion != EstadoConexion.EN_LINEA) {
            return false;
        }
        estadoConexion = EstadoConexion.DESCONECTADA;
        registrarEvento(new CamaraDesconectada(getHogarId(), getId(), nombreHabitacion, ahora));
        return true;
    }

    /** CA-15.3: for over 5 minutes the agent only got frames it had to discard. */
    public boolean marcarDeteccionNoConfiable() {
        boolean cambio = deteccionConfiable;
        deteccionConfiable = false;
        return cambio;
    }

    public void marcarDeteccionConfiable() {
        deteccionConfiable = true;
    }

    /** US-22 / CA-22.1: capture and detection stop until {@code hasta}; a new pause replaces the old one. */
    public void pausar(Instant hasta) {
        this.pausadaHasta = Objects.requireNonNull(hasta, "hasta");
    }

    /** Resume now (manual resume). */
    public void reanudar() {
        this.pausadaHasta = null;
    }

    /** CA-22.3: the pause is over; returns whether it ended now. */
    public boolean finalizarPausaSiVencio(Instant ahora) {
        if (pausadaHasta == null || ahora.isBefore(pausadaHasta)) {
            return false;
        }
        pausadaHasta = null;
        return true;
    }

    /** CA-22.1: a paused camera neither captures nor detects until its pause ends. */
    public boolean estaPausada(Instant ahora) {
        return pausadaHasta != null && ahora.isBefore(pausadaHasta);
    }

    public String getNombreHabitacion() {
        return nombreHabitacion;
    }

    public EstadoConexion getEstadoConexion() {
        return estadoConexion;
    }

    public Instant getUltimaSenal() {
        return ultimaSenal;
    }

    public Instant getPausadaHasta() {
        return pausadaHasta;
    }

    public boolean isDeteccionConfiable() {
        return deteccionConfiable;
    }
}
