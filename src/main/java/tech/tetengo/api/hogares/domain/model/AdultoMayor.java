package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * US-04 / CA-04.1: name, home address and living arrangement of the older adult, plus the age the
 * prototype's profile form asks for and the phone that «Llamar a Rosa» dials (API contract).
 */
@Embeddable
public class AdultoMayor {

    @Column(name = "adulto_mayor_nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "adulto_mayor_direccion", nullable = false, length = 250)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "adulto_mayor_convivencia", nullable = false, length = 20)
    private Convivencia convivencia;

    /** Null only for households registered before the age was asked. */
    @Column(name = "adulto_mayor_edad")
    private Integer edad;

    @Column(name = "adulto_mayor_telefono", length = 20)
    private String telefono;

    protected AdultoMayor() {}

    public AdultoMayor(String nombre, int edad, String direccion, Convivencia convivencia, String telefono) {
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
        this.edad = edad;
        this.direccion = Objects.requireNonNull(direccion, "direccion").strip();
        this.convivencia = Objects.requireNonNull(convivencia, "convivencia");
        this.telefono = telefono == null || telefono.isBlank() ? null : telefono.strip();
    }

    public String getNombre() {
        return nombre;
    }

    /**
     * The first word of the name, as the prototype and the app name the older adult in notices («Rosa»
     * for «Rosa Huamán»).
     */
    public String nombreDePila() {
        return nombre.strip().split("\\s+", 2)[0];
    }

    public String getDireccion() {
        return direccion;
    }

    public Convivencia getConvivencia() {
        return convivencia;
    }

    public Integer getEdad() {
        return edad;
    }

    public String getTelefono() {
        return telefono;
    }
}
