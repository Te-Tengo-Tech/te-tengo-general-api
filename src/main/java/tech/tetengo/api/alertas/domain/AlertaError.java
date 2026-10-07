package tech.tetengo.api.alertas.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum AlertaError implements CodigoError {
    ALERTA_NO_ENCONTRADA("ALERTA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La alerta no existe en este hogar."),
    CAMARA_NO_ENCONTRADA("CAMARA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La cámara no existe en este hogar.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    AlertaError(String codigo, HttpStatus estado, String mensaje) {
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
