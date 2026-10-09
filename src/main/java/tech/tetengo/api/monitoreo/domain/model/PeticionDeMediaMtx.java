package tech.tetengo.api.monitoreo.domain.model;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An authorization request of MediaMTX ({@code authMethod: http}): who wants to do what on which path.
 * Only two actions can ever be allowed: the household agent publishing a camera path, and a viewer
 * reading it with the token of an open session (API contract §4, ADR 0007).
 *
 * @param accion {@code publish}, {@code read}, {@code playback}, {@code api}, {@code metrics} or {@code pprof}
 * @param query the query string of the request, e.g. {@code token=…} for an HLS read
 */
public record PeticionDeMediaMtx(String accion, String usuario, String clave, String ruta, String query) {

    /** The user name the agent publishes with; the password is its publish token. */
    public static final String USUARIO_AGENTE = "agente";

    /** {@code camaras/<camaraId>}: one path per camera. */
    private static final Pattern RUTA_DE_CAMARA =
            Pattern.compile("camaras/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private static final String PARAMETRO_TOKEN = "token";

    public enum Tipo {
        PUBLICAR,
        LEER,
        OTRA
    }

    public Tipo tipo() {
        if ("publish".equals(accion)) {
            return Tipo.PUBLICAR;
        }
        if ("read".equals(accion) || "playback".equals(accion)) {
            return Tipo.LEER;
        }
        return Tipo.OTRA;
    }

    /** The camera of the path, if the path is a camera path. */
    public Optional<UUID> camara() {
        if (ruta == null) {
            return Optional.empty();
        }
        Matcher m = RUTA_DE_CAMARA.matcher(ruta);
        return m.matches() ? Optional.of(UUID.fromString(m.group(1))) : Optional.empty();
    }

    /** The publish token, when the agent's user name is used. */
    public Optional<String> claveDelAgente() {
        return USUARIO_AGENTE.equals(usuario) && clave != null && !clave.isBlank()
                ? Optional.of(clave)
                : Optional.empty();
    }

    /** The viewer token of the {@code token} query parameter. */
    public Optional<String> tokenDeEspectador() {
        return tokenDeQuery(query);
    }

    /** The {@code token} parameter of a query string ({@code a=1&token=…}), URL-decoded. */
    public static Optional<String> tokenDeQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(query.split("&"))
                .map(par -> par.split("=", 2))
                .filter(par -> par.length == 2 && PARAMETRO_TOKEN.equals(par[0]) && !par[1].isBlank())
                .map(par -> URLDecoder.decode(par[1], StandardCharsets.UTF_8))
                .findFirst();
    }
}
