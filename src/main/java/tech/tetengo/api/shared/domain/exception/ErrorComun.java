package tech.tetengo.api.shared.domain.exception;

import org.springframework.http.HttpStatus;

/** Errors shared by every module. */
public enum ErrorComun implements CodigoError {
    SOLO_TITULAR("SOLO_TITULAR", HttpStatus.FORBIDDEN, "Solo el familiar titular puede hacer este cambio.");

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
