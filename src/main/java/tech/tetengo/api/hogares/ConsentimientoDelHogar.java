package tech.tetengo.api.hogares;

/** Public API of {@code hogares}: whether the household in context has a monitoring consent in force. */
public interface ConsentimientoDelHogar {

    /** US-05: registered and neither revoked nor replaced; false without a household in context. */
    boolean vigente();
}
