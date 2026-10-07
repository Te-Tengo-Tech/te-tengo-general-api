package tech.tetengo.api.shared.infrastructure.web;

/**
 * Spring Framework 7 native header versioning, as in reqsai-api: clean paths ({@code /api/...})
 * and the client picks the version with {@code Api-Version: 1} (defaults to 1).
 */
public final class ApiVersioning {

    public static final String BASE = "/api";
    public static final String V1 = "1";
    public static final String CABECERA = "Api-Version";

    private ApiVersioning() {}
}
