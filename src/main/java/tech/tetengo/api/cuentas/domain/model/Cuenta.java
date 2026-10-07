package tech.tetengo.api.cuentas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * Account of a family member or caregiver (US-01). Global: one account can belong to several
 * households, so it does not extend {@code EntidadDelHogar}. The password is only kept as a hash.
 */
@Entity
@Table(name = "cuentas")
public class Cuenta extends AggregateRoot {

    /** CA-02.3: 5 consecutive failures lock the account for 15 minutes. */
    static final int INTENTOS_PERMITIDOS = 5;

    static final Duration DURACION_DEL_BLOQUEO = Duration.ofMinutes(15);

    @Column(nullable = false, length = 254)
    private String correo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "contrasena_cifrada", nullable = false, length = 100)
    private String contrasenaCifrada;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueada_hasta")
    private Instant bloqueadaHasta;

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

    /**
     * US-02: applies a sign-in attempt. A correct password resets the failure count (CA-02.1); a wrong
     * one is rejected (CA-02.2) and the fifth consecutive failure locks the account for 15 minutes,
     * telling the user when they can try again (CA-02.3). While locked, even the right password is
     * rejected.
     */
    public void autenticar(boolean contrasenaCorrecta, Instant ahora) {
        if (estaBloqueada(ahora)) {
            throw bloqueada();
        }
        if (contrasenaCorrecta) {
            intentosFallidos = 0;
            bloqueadaHasta = null;
            return;
        }
        intentosFallidos++;
        if (intentosFallidos >= INTENTOS_PERMITIDOS) {
            intentosFallidos = 0;
            bloqueadaHasta = ahora.plus(DURACION_DEL_BLOQUEO);
            throw bloqueada();
        }
        throw new ErrorDeNegocio(CuentaError.CREDENCIALES_INVALIDAS);
    }

    public boolean estaBloqueada(Instant ahora) {
        return bloqueadaHasta != null && ahora.isBefore(bloqueadaHasta);
    }

    /** US-03: a new password also lifts any lock. */
    public void cambiarContrasena(String nuevaContrasenaCifrada) {
        this.contrasenaCifrada = Objects.requireNonNull(nuevaContrasenaCifrada, "nuevaContrasenaCifrada");
        this.intentosFallidos = 0;
        this.bloqueadaHasta = null;
    }

    private ErrorDeNegocio bloqueada() {
        return new ErrorDeNegocio(CuentaError.CUENTA_BLOQUEADA, Map.of("bloqueadaHasta", bloqueadaHasta));
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

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getBloqueadaHasta() {
        return bloqueadaHasta;
    }
}
