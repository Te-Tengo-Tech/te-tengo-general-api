package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.camaras.AmbienteRenombrado;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * A room of the household watched by a camera (US-29): Sala, Dormitorio, Cocina… The backlog and the
 * API call it «ambiente»; the apps label it «habitación» (docs/BLOCKERS.md). Its name is the
 * {@code nombreHabitacion} of its cameras and of their alerts. The model allows several cameras per
 * room; for now the use cases allow one (CA-29.3). Names are unique per household, ignoring case.
 */
@Entity
@Table(name = "ambientes")
public class Ambiente extends EntidadDelHogar {

    @Column(nullable = false, length = Camara.LARGO_MAXIMO_NOMBRE)
    private String nombre;

    /** Lowercase name: the uniqueness key of the household's rooms. */
    @Column(name = "nombre_normalizado", nullable = false, length = Camara.LARGO_MAXIMO_NOMBRE)
    private String nombreNormalizado;

    @Column(name = "eliminado_en")
    private Instant eliminadoEn;

    protected Ambiente() {}

    public Ambiente(String nombre) {
        cambiarNombre(nombre);
    }

    /** Returns the previous name, or null if it did not change. */
    public String renombrar(String nuevo, UUID usuarioId, Instant ahora) {
        String anterior = nombre;
        cambiarNombre(nuevo);
        if (anterior.equals(nombre)) {
            return null;
        }
        registrarEvento(new AmbienteRenombrado(getHogarId(), getId(), anterior, nombre, usuarioId, ahora));
        return anterior;
    }

    /** A room without cameras in use can be deleted; its row stays for the cameras that used it. */
    public void eliminar(Instant ahora) {
        if (eliminadoEn == null) {
            eliminadoEn = Objects.requireNonNull(ahora);
        }
    }

    private void cambiarNombre(String nuevo) {
        this.nombre = valido(nuevo);
        this.nombreNormalizado = normalizar(this.nombre);
    }

    /** CA-29.2: the name cannot be empty («Escribe el nombre del ambiente.»), and at most 40 characters. */
    public static String valido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ErrorDeNegocio(CamaraError.AMBIENTE_NOMBRE_VACIO);
        }
        String limpio = nombre.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            throw new ErrorDeNegocio(CamaraError.AMBIENTE_NOMBRE_VACIO);
        }
        if (limpio.length() > Camara.LARGO_MAXIMO_NOMBRE) {
            throw new ErrorDeNegocio(CamaraError.AMBIENTE_NOMBRE_MUY_LARGO);
        }
        return limpio;
    }

    public static String normalizar(String nombre) {
        return valido(nombre).toLowerCase(Locale.ROOT);
    }

    public boolean eliminado() {
        return eliminadoEn != null;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNombreNormalizado() {
        return nombreNormalizado;
    }
}
