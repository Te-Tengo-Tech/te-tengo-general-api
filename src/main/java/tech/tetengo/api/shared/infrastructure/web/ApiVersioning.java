package tech.tetengo.api.shared.infrastructure.web;

/**
 * Versionado nativo de Spring Framework 7 por cabecera, igual que en reqsai-api: rutas limpias
 * ({@code /api/...}) y el cliente elige la versión con {@code Api-Version: 1} (sin cabecera, la 1).
 */
public final class ApiVersioning {

    public static final String BASE = "/api";
    public static final String V1 = "1";
    public static final String CABECERA = "Api-Version";

    private ApiVersioning() {}
}
