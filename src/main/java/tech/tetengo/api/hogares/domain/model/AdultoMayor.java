package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/** US-04 / CA-04.1: name, home address and living arrangement of the older adult. */
@Embeddable
public class AdultoMayor {

    @Column(name = "adulto_mayor_nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "adulto_mayor_direccion", nullable = false, length = 250)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "adulto_mayor_convivencia", nullable = false, length = 20)
    private Convivencia convivencia;

    protected AdultoMayor() {}

    public AdultoMayor(String nombre, String direccion, Convivencia convivencia) {
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
        this.direccion = Objects.requireNonNull(direccion, "direccion").strip();
        this.convivencia = Objects.requireNonNull(convivencia, "convivencia");
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public Convivencia getConvivencia() {
        return convivencia;
    }
}
