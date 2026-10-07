package tech.tetengo.api.shared.domain.exception;

/** Violated business rule. Rendered as a {@code ProblemDetail} with the catalog code. */
public class ErrorDeNegocio extends RuntimeException {

    private final transient CodigoError error;

    public ErrorDeNegocio(CodigoError error) {
        super(error.mensaje());
        this.error = error;
    }

    public CodigoError error() {
        return error;
    }
}
