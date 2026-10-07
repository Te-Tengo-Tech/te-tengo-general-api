package tech.tetengo.api.shared.domain.exception;

import org.springframework.http.HttpStatus;

/** Catálogo de errores de negocio. Cada módulo define el suyo como un {@code enum}. */
public interface CodigoError {

    /** Código estable que leen los clientes, por ejemplo {@code CAMARA_NOMBRE_VACIO}. */
    String codigo();

    HttpStatus estado();

    String mensaje();
}
