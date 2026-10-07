package tech.tetengo.api.cuentas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * Account of a family member or caregiver (US-01). Global: one account can belong to several
 * households, so it does not extend {@code EntidadDelHogar}. The password is only kept as a hash.
 */
@Entity
@Table(name = "cuentas")
public class Cuenta extends AggregateRoot {

    @Column(nullable = false, length = 254)
    private String correo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "contrasena_cifrada", nullable = false, length = 100)
    private String contrasenaCifrada;

    protected Cuenta() {}

    public Cuenta(String correo, String nombre, String contrasenaCifrada) {
        this.correo = normalizarCorreo(correo);
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
        this.contrasenaCifrada = Objects.requireNonNull(contrasenaCifrada, "contrasenaCifrada");
    }

    /** E-mails are compared trimmed and case-insensitively (CA-01.2). */
    public static String normalizarCorreo(String correo) {
        return Objects.requireNonNull(correo, "correo").strip().toLowerCase(Locale.ROOT);
    }

    public String getCorreo() {
        return correo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getContrasenaCifrada() {
        return contrasenaCifrada;
    }
}
