package tech.tetengo.api.actividad.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum ActividadError implements CodigoError {
    VALIDACION("VALIDACION", HttpStatus.BAD_REQUEST, "Revisa los datos ingresados.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    ActividadError(String codigo, HttpStatus estado, String mensaje) {
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
