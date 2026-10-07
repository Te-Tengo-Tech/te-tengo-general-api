package tech.tetengo.api.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.MissingApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
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

    /** A missing or unsupported {@code Api-Version} header is a validation error of that header. */
    @ExceptionHandler({InvalidApiVersionException.class, MissingApiVersionException.class})
    public ProblemDetail versionNoSoportada(ResponseStatusException ex, HttpServletRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Versión de la API no soportada. Usa la cabecera Api-Version: 1.");
        problema.setProperty("codigo", VALIDACION);
        problema.setProperty("campos", Map.of(ApiVersioning.CABECERA, "Usa la versión " + ApiVersioning.V1 + "."));
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

    /** Query and path parameters validated by Spring MVC's built-in method validation. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest peticion) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(resultado -> {
            String nombre = resultado.getMethodParameter().getParameterName();
            resultado
                    .getResolvableErrors()
                    .forEach(error ->
                            campos.putIfAbsent(nombre == null ? "parametro" : nombre, error.getDefaultMessage()));
        });
        return handleExceptionInternal(ex, validacion(campos, peticion), headers, HttpStatus.BAD_REQUEST, peticion);
    }

    /** A value that does not parse (a malformed UUID or date) is a validation error of that field. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest peticion) {
        String nombre = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        Map<String, String> campos = nombre == null ? Map.of() : Map.of(nombre, "El valor no tiene un formato válido.");
        return handleExceptionInternal(ex, validacion(campos, peticion), headers, HttpStatus.BAD_REQUEST, peticion);
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
