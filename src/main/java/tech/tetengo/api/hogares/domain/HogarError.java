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
            "SIN_CONSENTIMIENTO", HttpStatus.NOT_FOUND, "Aún no se registró el consentimiento del adulto mayor.");

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
