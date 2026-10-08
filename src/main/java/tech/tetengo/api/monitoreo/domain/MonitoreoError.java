package tech.tetengo.api.monitoreo.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum MonitoreoError implements CodigoError {
    DURACION_INVALIDA(
            "DURACION_INVALIDA",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Elige una duración de pausa: 30 minutos, 1 hora, 2 horas o hasta mañana."),
    CAMARA_NO_ENCONTRADA("CAMARA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La cámara no existe en este hogar."),
    CAMARA_DESCONECTADA(
            "CAMARA_DESCONECTADA",
            HttpStatus.CONFLICT,
            "La cámara está desconectada; la vista en vivo no está disponible."),
    CAMARA_EN_PAUSA(
            "CAMARA_EN_PAUSA", HttpStatus.CONFLICT, "La cámara está en pausa; la vista en vivo no está disponible."),
    SIN_CONSENTIMIENTO(
            "SIN_CONSENTIMIENTO",
            HttpStatus.CONFLICT,
            "Sin el consentimiento del adulto mayor la cámara no transmite."),
    /** {@code alertaId} of another camera, or an unknown {@code modo} (with {@code campos}). */
    VALIDACION("VALIDACION", HttpStatus.BAD_REQUEST, "Revisa los datos ingresados."),
    SESION_NO_ENCONTRADA(
            "SESION_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La sesión de vista en vivo no existe o ya terminó.");

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
