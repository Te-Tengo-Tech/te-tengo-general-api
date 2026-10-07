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

/** Cámara instalada en el hogar. Las reglas viven aquí, no en los controladores. */
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

    /** CA-06.2 y CA-06.3: el nombre no puede quedar vacío. */
    public void renombrar(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ErrorDeNegocio(CamaraError.NOMBRE_VACIO);
        }
        if (nombre.strip().length() > LARGO_MAXIMO_NOMBRE) {
            throw new ErrorDeNegocio(CamaraError.NOMBRE_MUY_LARGO);
        }
        this.nombreHabitacion = nombre.strip();
    }

    /** CA-07.1: el agente reporta señal; la cámara queda en línea con la hora de la última señal. */
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
