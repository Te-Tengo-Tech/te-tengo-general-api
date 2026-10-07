package tech.tetengo.api.hogares.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum HogarError implements CodigoError {
    HOGAR_YA_REGISTRADO("HOGAR_YA_REGISTRADO", HttpStatus.CONFLICT, "Cada cuenta gestiona un único adulto mayor."),
    SIN_MEMBRESIA("SIN_MEMBRESIA", HttpStatus.FORBIDDEN, "No perteneces a ese hogar.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    HogarError(String codigo, HttpStatus estado, String mensaje) {
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
