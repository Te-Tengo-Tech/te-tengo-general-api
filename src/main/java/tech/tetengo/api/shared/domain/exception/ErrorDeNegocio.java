package tech.tetengo.api.shared.domain.exception;

/** Regla de negocio incumplida. Se responde como {@code ProblemDetail} con el código del catálogo. */
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
