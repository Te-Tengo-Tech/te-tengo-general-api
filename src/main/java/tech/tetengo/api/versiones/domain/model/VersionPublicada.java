package tech.tetengo.api.versiones.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The latest version of an app on one channel (US-43): what to download and check, the notes of
 * «Qué cambió», and the minimum version still supported (CA-43.8).
 *
 * @param versionMinima an installed version below it must update; null when every version works
 * @param url the file (Windows, macOS, APK), the store page (Google Play, App Store) or the web app
 * @param sha256 the file's SHA-256 in lowercase hex; null for channels without a file
 */
public record VersionPublicada(
        NumeroDeVersion version,
        NumeroDeVersion versionMinima,
        URI url,
        String sha256,
        Long tamanoBytes,
        Instant publicadaEn,
        List<String> notas) {

    /** «Qué cambió» shows a few short lines (implementation choice). */
    static final int NOTAS_MAXIMAS = 10;

    static final int LARGO_MAXIMO_NOTA = 200;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    public VersionPublicada {
        Objects.requireNonNull(version, "version");
        notas = notas == null ? List.of() : List.copyOf(notas);
    }

    /** A channel known only by its version: no file, notes or date (the backend's fallback setting). */
    public static VersionPublicada soloVersion(NumeroDeVersion version) {
        return new VersionPublicada(version, null, null, null, null, null, List.of());
    }

    /**
     * Checks a manifest entry: a {@code MAJOR.MINOR.PATCH} version, an absolute HTTPS address (HTTP
     * only for a local host), and for a channel with a file both the address and a 64-hex SHA-256 or
     * neither. Notes are trimmed, one line each, at most {@value #NOTAS_MAXIMAS} of {@value
     * #LARGO_MAXIMO_NOTA} characters.
     *
     * @throws IllegalArgumentException with the reason when the entry cannot be published
     */
    public static VersionPublicada de(Canal canal, EntradaDeManifiesto entrada) {
        NumeroDeVersion version = NumeroDeVersion.leer(entrada.version())
                .orElseThrow(() -> new IllegalArgumentException("version is not MAJOR.MINOR.PATCH"));
        URI url = direccion(entrada.url());
        String sha256 = null;
        Long tamano = null;
        if (canal.esArchivo()) {
            sha256 = entrada.sha256() == null ? null : entrada.sha256().strip().toLowerCase(Locale.ROOT);
            if ((url == null) != (sha256 == null)) {
                throw new IllegalArgumentException("a file needs both url and sha256");
            }
            if (sha256 != null && !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("sha256 is not 64 hex characters");
            }
            if (entrada.tamanoBytes() != null && entrada.tamanoBytes() < 0) {
                throw new IllegalArgumentException("tamanoBytes is negative");
            }
            tamano = url == null ? null : entrada.tamanoBytes();
        }
        return new VersionPublicada(
                version, null, url, sha256, tamano, instante(entrada.publicadaEn()), notas(entrada.notas()));
    }

    /**
     * The same version with the minimum the backend supports (CA-43.8). A minimum above the published
     * version is a configuration mistake: nobody could reach it, so it is capped at the published one.
     */
    public VersionPublicada conMinima(NumeroDeVersion minima) {
        if (minima == null) {
            return this;
        }
        NumeroDeVersion efectiva = version.esAnteriorA(minima) ? version : minima;
        return new VersionPublicada(version, efectiva, url, sha256, tamanoBytes, publicadaEn, notas);
    }

    private static URI direccion(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(texto.strip());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url is not a valid address");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !(esquema.equals("https") || (esquema.equals("http") && local))) {
            throw new IllegalArgumentException("url must be an absolute https address");
        }
        return uri;
    }

    private static Instant instante(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(texto.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("publicadaEn is not an ISO-8601 instant");
        }
    }

    private static List<String> notas(List<String> notas) {
        return notas.stream()
                .map(n -> n.replaceAll("\\p{Cntrl}", " ").strip())
                .filter(n -> !n.isEmpty())
                .map(n -> n.length() > LARGO_MAXIMO_NOTA ? n.substring(0, LARGO_MAXIMO_NOTA) : n)
                .limit(NOTAS_MAXIMAS)
                .toList();
    }
}
