package tech.tetengo.api.camaras.domain.model;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The {@code user_code} of the PC link (RFC 8628 §3.2, §6.1): 8 characters of the 20 consonants
 * {@value #ALFABETO}, without vowels (no words) or digits (no 0/O, 1/I confusion), about 34.6 bits.
 * It is shown and typed as {@code XXXX-XXXX}; the app accepts it in lowercase and with or without
 * the hyphen (CA-28.2).
 */
public record CodigoDeUsuario(String valor) {

    public static final String ALFABETO = "BCDFGHJKLMNPQRSTVWXZ";
    public static final int LARGO = 8;

    private static final RandomGenerator ALEATORIO = new SecureRandom();

    public CodigoDeUsuario {
        if (!valido(valor)) {
            throw new IllegalArgumentException("Código de vinculación inválido");
        }
    }

    public static CodigoDeUsuario generar() {
        return generar(ALEATORIO);
    }

    static CodigoDeUsuario generar(RandomGenerator aleatorio) {
        StringBuilder codigo = new StringBuilder(LARGO);
        for (int i = 0; i < LARGO; i++) {
            codigo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return new CodigoDeUsuario(codigo.toString());
    }

    /**
     * CA-28.2: what the member typed or the QR carried, in lowercase or uppercase, with or without the
     * hyphen and spaces. Empty if it is not 8 letters of the alphabet.
     */
    public static Optional<CodigoDeUsuario> leer(String escrito) {
        if (escrito == null) {
            return Optional.empty();
        }
        String limpio = escrito.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return valido(limpio) ? Optional.of(new CodigoDeUsuario(limpio)) : Optional.empty();
    }

    private static boolean valido(String valor) {
        if (valor == null || valor.length() != LARGO) {
            return false;
        }
        for (int i = 0; i < valor.length(); i++) {
            if (ALFABETO.indexOf(valor.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /** {@code XXXX-XXXX}, as the PC shows it. */
    public String legible() {
        return valor.substring(0, LARGO / 2) + "-" + valor.substring(LARGO / 2);
    }

    @Override
    public String toString() {
        return legible();
    }
}
