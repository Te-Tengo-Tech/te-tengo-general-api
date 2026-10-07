package tech.tetengo.api.shared.domain.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Violated business rule. Rendered as a {@code ProblemDetail} with the catalog code and, when the API
 * contract says so, extra properties (e.g. {@code bloqueadaHasta} for {@code CUENTA_BLOQUEADA}).
 */
public class ErrorDeNegocio extends RuntimeException {

    private final transient CodigoError error;
    private final transient Map<String, Object> propiedades;

    public ErrorDeNegocio(CodigoError error) {
        this(error, Map.of());
    }

    public ErrorDeNegocio(CodigoError error, Map<String, Object> propiedades) {
        super(error.mensaje());
        this.error = error;
        this.propiedades = Collections.unmodifiableMap(new LinkedHashMap<>(propiedades));
    }

    public CodigoError error() {
        return error;
    }

    /** Extra {@code ProblemDetail} properties required by the API contract. */
    public Map<String, Object> propiedades() {
        return propiedades;
    }
}
