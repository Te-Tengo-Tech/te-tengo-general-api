package tech.tetengo.api.cuentas.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum CuentaError implements CodigoError {
    CORREO_EN_USO("CORREO_EN_USO", HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    CuentaError(String codigo, HttpStatus estado, String mensaje) {
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
