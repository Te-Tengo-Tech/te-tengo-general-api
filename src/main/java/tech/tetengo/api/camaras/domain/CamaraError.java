package tech.tetengo.api.camaras.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum CamaraError implements CodigoError {
    NO_ENCONTRADA("CAMARA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La cámara no existe en este hogar."),
    NOMBRE_VACIO("CAMARA_NOMBRE_VACIO", HttpStatus.UNPROCESSABLE_CONTENT, "El nombre de la habitación es obligatorio."),
    NOMBRE_MUY_LARGO(
            "CAMARA_NOMBRE_MUY_LARGO",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "El nombre de la habitación es demasiado largo.");

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
