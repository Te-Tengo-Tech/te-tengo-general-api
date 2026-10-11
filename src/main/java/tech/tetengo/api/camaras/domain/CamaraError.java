package tech.tetengo.api.camaras.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum CamaraError implements CodigoError {
    NO_ENCONTRADA("CAMARA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La cámara no existe en este hogar."),
    NOMBRE_VACIO("CAMARA_NOMBRE_VACIO", HttpStatus.UNPROCESSABLE_CONTENT, "El nombre de la habitación es obligatorio."),
    NOMBRE_MUY_LARGO(
            "CAMARA_NOMBRE_MUY_LARGO",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "El nombre de la habitación es demasiado largo."),
    CREDENCIAL_INVALIDA("CREDENCIAL_INVALIDA", HttpStatus.UNAUTHORIZED, "La credencial de instalación no es válida."),
    PC_NO_ENCONTRADA("PC_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La PC no está vinculada a este hogar."),
    PC_NOMBRE_VACIO("PC_NOMBRE_VACIO", HttpStatus.UNPROCESSABLE_CONTENT, "El nombre de la PC es obligatorio."),
    PC_NOMBRE_MUY_LARGO(
            "PC_NOMBRE_MUY_LARGO", HttpStatus.UNPROCESSABLE_CONTENT, "El nombre de la PC es demasiado largo."),
    /** CA-28.6 counts it: «Ese código no existe. Revísalo letra por letra.» */
    CODIGO_INCORRECTO("CODIGO_INCORRECTO", HttpStatus.NOT_FOUND, "Ese código no existe. Revísalo letra por letra."),
    /** CA-28.3: expired after its 10 minutes, or already used. */
    CODIGO_VENCIDO(
            "CODIGO_VENCIDO",
            HttpStatus.GONE,
            "Este código ya venció o ya se usó. La PC ya muestra uno nuevo: escríbelo o escanéalo."),
    /** CA-28.6: 5 wrong codes in a row block code entry for 15 minutes. */
    VINCULACION_BLOQUEADA(
            "VINCULACION_BLOQUEADA", HttpStatus.LOCKED, "Demasiados intentos. Vuelve a intentarlo más tarde.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    CamaraError(String codigo, HttpStatus estado, String mensaje) {
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
