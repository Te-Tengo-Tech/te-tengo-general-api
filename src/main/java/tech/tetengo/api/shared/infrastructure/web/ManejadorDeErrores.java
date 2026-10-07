package tech.tetengo.api.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * Every error response follows RFC 9457 ({@link ProblemDetail}) with a {@code codigo} property.
 * Validation failures are {@code 400 VALIDACION} with {@code campos: {field: message}} so the app can
 * highlight the missing field (CA-01.3, CA-04.3). Internal errors never expose their message.
 */
@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    public static final String VALIDACION = "VALIDACION";

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(ErrorDeNegocio.class)
    public ProblemDetail errorDeNegocio(ErrorDeNegocio ex, HttpServletRequest peticion) {
        var error = ex.error();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(error.estado(), error.mensaje());
        problema.setProperty("codigo", error.codigo());
        ex.propiedades().forEach(problema::setProperty);
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

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest peticion) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problema = validacion(campos, peticion);
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, peticion);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest peticion) {
        return handleExceptionInternal(ex, validacion(Map.of(), peticion), headers, HttpStatus.BAD_REQUEST, peticion);
    }

    /** Framework errors (404, 405, type mismatches…) also carry a {@code codigo}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object cuerpo, HttpHeaders headers, HttpStatusCode status, WebRequest peticion) {
        if (cuerpo instanceof ProblemDetail problema) {
            if (problema.getProperties() == null || !problema.getProperties().containsKey("codigo")) {
                problema.setProperty("codigo", status.value() == 400 ? VALIDACION : codigoPorEstado(status));
            }
            if (problema.getInstance() == null && peticion instanceof ServletWebRequest web) {
                problema.setInstance(URI.create(web.getRequest().getRequestURI()));
            }
        }
        return super.handleExceptionInternal(ex, cuerpo, headers, status, peticion);
    }

    private static ProblemDetail validacion(Map<String, String> campos, WebRequest peticion) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los datos ingresados.");
        problema.setProperty("codigo", VALIDACION);
        problema.setProperty("campos", campos);
        if (peticion instanceof ServletWebRequest web) {
            problema.setInstance(URI.create(web.getRequest().getRequestURI()));
        }
        return problema;
    }

    private static String codigoPorEstado(HttpStatusCode status) {
        HttpStatus conocido = HttpStatus.resolve(status.value());
        return conocido != null ? conocido.name() : "ERROR_" + status.value();
    }
}
