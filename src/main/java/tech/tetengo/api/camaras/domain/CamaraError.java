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
    AMBIENTE_NO_ENCONTRADO("AMBIENTE_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "El ambiente no existe en este hogar."),
    AMBIENTE_NOMBRE_VACIO("AMBIENTE_NOMBRE_VACIO", HttpStatus.UNPROCESSABLE_CONTENT, "Escribe el nombre del ambiente."),
    AMBIENTE_NOMBRE_MUY_LARGO(
            "AMBIENTE_NOMBRE_MUY_LARGO",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "El nombre del ambiente es demasiado largo."),
    /** Brief §6.2: no cameras in the bathroom. */
    AMBIENTE_NO_PERMITIDO(
            "AMBIENTE_NO_PERMITIDO",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Por privacidad, Te Tengo no admite cámaras en el baño."),
    AMBIENTE_YA_EXISTE("AMBIENTE_YA_EXISTE", HttpStatus.CONFLICT, "Ya hay un ambiente con ese nombre."),
    /** CA-29.3: one camera per room, for now. */
    AMBIENTE_OCUPADO(
            "AMBIENTE_OCUPADO",
            HttpStatus.CONFLICT,
            "Ese ambiente ya tiene una cámara. Por ahora, cada ambiente usa una sola cámara."),
    AMBIENTE_CON_CAMARA(
            "AMBIENTE_CON_CAMARA",
            HttpStatus.CONFLICT,
            "El ambiente tiene una cámara en uso; asígnala a otro ambiente antes de borrarlo."),
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
