package tech.tetengo.api.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * Todas las respuestas de error siguen RFC 9457 ({@link ProblemDetail}) con la propiedad {@code codigo}.
 * Los errores internos nunca exponen su mensaje al cliente.
 */
@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(ErrorDeNegocio.class)
    public ProblemDetail errorDeNegocio(ErrorDeNegocio ex, HttpServletRequest peticion) {
        var error = ex.error();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(error.estado(), error.mensaje());
        problema.setProperty("codigo", error.codigo());
        problema.setInstance(URI.create(peticion.getRequestURI()));
        return problema;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail errorInterno(Exception ex, HttpServletRequest peticion) {
        log.error("Error no controlado en {}", peticion.getRequestURI(), ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error en el servidor. Inténtalo más tarde.");
        problema.setProperty("codigo", "ERROR_INTERNO");
        problema.setInstance(URI.create(peticion.getRequestURI()));
        return problema;
    }
}
