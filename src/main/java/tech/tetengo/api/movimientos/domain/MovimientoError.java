package tech.tetengo.api.movimientos.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum MovimientoError implements CodigoError {
    /** {@code 400 VALIDACION} with {@code campos}, for rules that need the clock or the settings. */
    VALIDACION("VALIDACION", HttpStatus.BAD_REQUEST, "Revisa los datos ingresados."),
    SIN_CONSENTIMIENTO(
            "SIN_CONSENTIMIENTO",
            HttpStatus.CONFLICT,
            "Primero registra el consentimiento de monitoreo del adulto mayor."),
    SIN_CONSENTIMIENTO_DE_MOVIMIENTOS(
            "SIN_CONSENTIMIENTO_DE_MOVIMIENTOS",
            HttpStatus.CONFLICT,
            "El adulto mayor no autorizó guardar sus movimientos."),
    CONSENTIMIENTO_DE_MOVIMIENTOS_NO_ENCONTRADO(
            "SIN_CONSENTIMIENTO_DE_MOVIMIENTOS",
            HttpStatus.NOT_FOUND,
            "No hay un consentimiento para guardar movimientos que revocar."),
    CONSENTIMIENTO_NO_ACEPTADO(
            "CONSENTIMIENTO_NO_ACEPTADO",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "El adulto mayor debe aceptar que se guarden sus movimientos."),
    VERSION_DE_TEXTO_INVALIDA(
            "VERSION_DE_TEXTO_INVALIDA",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "El texto aceptado no es la versión vigente; vuelve a mostrarlo."),
    RETENCION_INVALIDA(
            "RETENCION_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT, "Elige 7, 30, 90 o 365 días, o no guardar."),
    NUBE_DESACTIVADA(
            "MOVIMIENTOS_EN_LA_NUBE_DESACTIVADOS",
            HttpStatus.CONFLICT,
            "El titular no eligió guardar los movimientos en la nube."),
    FUERA_DEL_PLAZO(
            "MOVIMIENTOS_FUERA_DEL_PLAZO",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Ese momento ya está fuera del plazo de guardado en la nube."),
    ARCHIVO_YA_SUBIDO("ARCHIVO_YA_SUBIDO", HttpStatus.CONFLICT, "Ese archivo de movimientos ya está guardado."),
    ARCHIVO_NO_ENCONTRADO(
            "ARCHIVO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "Ese archivo de movimientos no existe en esta cámara."),
    ARCHIVO_NO_SUBIDO(
            "ARCHIVO_NO_SUBIDO",
            HttpStatus.CONFLICT,
            "El almacenamiento todavía no tiene ese archivo; súbelo de nuevo."),
    CAMARA_NO_ENCONTRADA("CAMARA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La cámara no existe en este hogar."),
    REPETICION_NO_ENCONTRADA("REPETICION_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "La repetición no existe o ya terminó.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    MovimientoError(String codigo, HttpStatus estado, String mensaje) {
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
