package tech.tetengo.api.shared.domain.exception;

import org.springframework.http.HttpStatus;

/** Business error catalog. Each module defines its own as an {@code enum}. */
public interface CodigoError {

    /** Stable code read by clients, e.g. {@code CAMARA_NOMBRE_VACIO}. */
    String codigo();

    HttpStatus estado();

    String mensaje();
}
