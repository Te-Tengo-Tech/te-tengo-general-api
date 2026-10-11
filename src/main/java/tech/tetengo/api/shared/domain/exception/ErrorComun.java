package tech.tetengo.api.shared.domain.exception;

import org.springframework.http.HttpStatus;

/** Errors shared by every module. */
public enum ErrorComun implements CodigoError {
    SOLO_TITULAR("SOLO_TITULAR", HttpStatus.FORBIDDEN, "Solo el familiar titular puede hacer este cambio."),
    /** An agent endpoint of one camera called with a PC token (AGENT_CONTRACT.md). */
    TOKEN_DE_CAMARA_REQUERIDO(
            "TOKEN_DE_CAMARA_REQUERIDO", HttpStatus.FORBIDDEN, "Este endpoint necesita el token de una cámara."),
    /** An agent endpoint of the PC called with a camera token or an installation token. */
    TOKEN_DE_PC_REQUERIDO("TOKEN_DE_PC_REQUERIDO", HttpStatus.FORBIDDEN, "Este endpoint necesita el token de la PC.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    ErrorComun(String codigo, HttpStatus estado, String mensaje) {
        this.codigo = codigo;
        this.estado = estado;
        this.mensaje = mensaje;
    }

    @Override
    public String codigo() {
        return codigo;
    }

    @Override
    public HttpStatus estado() {
        return estado;
    }

    @Override
    public String mensaje() {
        return mensaje;
    }
}
