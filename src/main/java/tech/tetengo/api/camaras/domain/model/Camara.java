package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
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

    /** CA-07.1: the agent reports a heartbeat; the camera goes online with the time of the last signal. */
    public void registrarSenal(Instant instante) {
        this.ultimaSenal = instante;
        this.estadoConexion = EstadoConexion.EN_LINEA;
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
}
