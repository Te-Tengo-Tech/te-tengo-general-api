package tech.tetengo.api.alertas.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum AlertaError implements CodigoError {
    ALERTA_NO_ENCONTRADA("ALERTA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La alerta no existe en este hogar."),
    CAMARA_NO_ENCONTRADA("CAMARA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La cámara no existe en este hogar."),
    EVENTO_NO_ENCONTRADO("EVENTO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "El evento no existe o no generó una alerta."),
    CLIP_NO_DISPONIBLE("CLIP_NO_DISPONIBLE", HttpStatus.NOT_FOUND, "El video de esta alerta no está disponible."),
    CLIP_ELIMINADO("CLIP_ELIMINADO", HttpStatus.GONE, "La grabación ya no está disponible."),
    ALERTA_CERRADA("ALERTA_CERRADA", HttpStatus.CONFLICT, "La alerta ya fue atendida o marcada como falsa alarma."),
    DISPOSITIVO_NO_ENCONTRADO(
            "DISPOSITIVO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "Este dispositivo no está registrado en tu cuenta.");

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
