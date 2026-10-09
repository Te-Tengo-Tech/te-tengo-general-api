package tech.tetengo.api.hogares.domain;

import org.springframework.http.HttpStatus;
import tech.tetengo.api.shared.domain.exception.CodigoError;

public enum HogarError implements CodigoError {
    HOGAR_YA_REGISTRADO("HOGAR_YA_REGISTRADO", HttpStatus.CONFLICT, "Cada cuenta gestiona un único adulto mayor."),
    SIN_MEMBRESIA("SIN_MEMBRESIA", HttpStatus.FORBIDDEN, "No perteneces a ese hogar."),
    CONSENTIMIENTO_NO_ACEPTADO(
            "CONSENTIMIENTO_NO_ACEPTADO",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "El consentimiento solo se registra si el adulto mayor lo acepta, incluida la vista en vivo."),
    SIN_CONSENTIMIENTO(
            "SIN_CONSENTIMIENTO", HttpStatus.NOT_FOUND, "Aún no se registró el consentimiento del adulto mayor."),
    YA_ES_FAMILIAR("YA_ES_FAMILIAR", HttpStatus.CONFLICT, "Esa persona ya es familiar de este hogar."),
    INVITACION_VENCIDA(
            "INVITACION_VENCIDA",
            HttpStatus.GONE,
            "La invitación ya no es válida. Pide una nueva al familiar titular."),
    NO_SE_PUEDE_RETIRAR_TITULAR(
            "NO_SE_PUEDE_RETIRAR_TITULAR", HttpStatus.CONFLICT, "El familiar titular no puede retirarse del hogar."),
    ESPERA_INVALIDA(
            "ESPERA_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT, "El tiempo de espera debe ser de 3, 5 o 10 minutos."),
    CONTACTO_NO_ES_FAMILIAR(
            "CONTACTO_NO_ES_FAMILIAR",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Los contactos deben ser familiares distintos vinculados a este hogar.");

    private final String codigo;
    private final HttpStatus estado;
    private final String mensaje;

    HogarError(String codigo, HttpStatus estado, String mensaje) {
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
