package tech.tetengo.api.versiones.domain.model;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version as {@code MAJOR.MINOR.PATCH}, compared numerically (US-43). A leading {@code v}, a
 * pre-release ({@code -rc.1}) or a build suffix ({@code +12}) is ignored: {@code 0.5.3+12} is
 * {@code 0.5.3}.
 */
public record NumeroDeVersion(int mayor, int menor, int parche) implements Comparable<NumeroDeVersion> {

    private static final Pattern FORMATO =
            Pattern.compile("^v?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:[-+][0-9A-Za-z.+-]*)?$");
    private static final Comparator<NumeroDeVersion> ORDEN = Comparator.comparingInt(NumeroDeVersion::mayor)
            .thenComparingInt(NumeroDeVersion::menor)
            .thenComparingInt(NumeroDeVersion::parche);

    public NumeroDeVersion {
        if (mayor < 0 || menor < 0 || parche < 0) {
            throw new IllegalArgumentException("A version has no negative parts");
        }
    }

    /** The version written in {@code texto}; empty when it is not {@code MAJOR.MINOR.PATCH}. */
    public static Optional<NumeroDeVersion> leer(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        Matcher m = FORMATO.matcher(texto.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new NumeroDeVersion(
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
    }

    public boolean esAnteriorA(NumeroDeVersion otra) {
        return compareTo(otra) < 0;
    }

    @Override
    public int compareTo(NumeroDeVersion otra) {
        return ORDEN.compare(this, otra);
    }

    @Override
    public String toString() {
        return mayor + "." + menor + "." + parche;
    }
}
