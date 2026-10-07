package tech.tetengo.api.cuentas.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum CuentaError implements CodigoError {
    CORREO_EN_USO("CORREO_EN_USO", HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo."),
    CREDENCIALES_INVALIDAS(
            "CREDENCIALES_INVALIDAS", HttpStatus.UNAUTHORIZED, "El correo o la contraseña no son correctos."),
    CUENTA_BLOQUEADA(
            "CUENTA_BLOQUEADA",
            HttpStatus.LOCKED,
            "La cuenta está bloqueada por varios intentos fallidos. Vuelve a intentarlo más tarde."),
    SESION_EXPIRADA("SESION_EXPIRADA", HttpStatus.UNAUTHORIZED, "La sesión expiró. Vuelve a iniciar sesión.");

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
