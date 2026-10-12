package tech.tetengo.api.camaras.application;

/**
 * An error of the PC link's OAuth endpoints, answered as RFC 6749 §5.2 asks ({@code {"error": …,
 * "error_description": …}}) instead of a problem detail, because the agent's OAuth client reads it.
 * {@code authorization_pending}, {@code slow_down}, {@code access_denied} and {@code expired_token}
 * are the device grant's (RFC 8628 §3.5).
 */
public class ErrorOAuth extends RuntimeException {

    public static final String SOLICITUD_INVALIDA = "invalid_request";
    public static final String CLIENTE_INVALIDO = "invalid_client";
    public static final String CONCESION_INVALIDA = "invalid_grant";
    public static final String CONCESION_NO_SOPORTADA = "unsupported_grant_type";
    public static final String AUTORIZACION_PENDIENTE = "authorization_pending";
    public static final String MAS_LENTO = "slow_down";
    public static final String ACCESO_DENEGADO = "access_denied";
    public static final String CODIGO_VENCIDO = "expired_token";

    private final String codigo;
    private final int estado;

    public ErrorOAuth(String codigo, String descripcion) {
        this(codigo, CLIENTE_INVALIDO.equals(codigo) ? 401 : 400, descripcion);
    }

    public ErrorOAuth(String codigo, int estado, String descripcion) {
        super(descripcion);
        this.codigo = codigo;
        this.estado = estado;
    }

    public String codigo() {
        return codigo;
    }

    /** HTTP status: 401 for {@code invalid_client}, 400 for the rest (RFC 6749 §5.2). */
    public int estado() {
        return estado;
    }

    public String descripcion() {
        return getMessage();
    }
}
