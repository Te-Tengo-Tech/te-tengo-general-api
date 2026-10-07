package tech.tetengo.api.historial.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum HistorialError implements CodigoError {
    SEMANA_INVALIDA("VALIDACION", HttpStatus.BAD_REQUEST, "La semana debe tener el formato ISO 2026-W41.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    HistorialError(String codigo, HttpStatus estado, String mensaje) {
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
