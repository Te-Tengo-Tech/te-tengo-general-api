package tech.tetengo.api.monitoreo.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum MonitoreoError implements CodigoError {
    DURACION_INVALIDA(
            "DURACION_INVALIDA",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Elige una duración de pausa: 30 minutos, 1 hora, 2 horas o hasta mañana."),
    CAMARA_NO_ENCONTRADA("CAMARA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La cámara no existe en este hogar.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    MonitoreoError(String codigo, HttpStatus estado, String mensaje) {
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
