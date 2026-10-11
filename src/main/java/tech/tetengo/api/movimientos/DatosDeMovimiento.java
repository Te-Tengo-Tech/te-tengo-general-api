package tech.tetengo.api.movimientos;

/**
 * Public API of {@code movimientos} for the household in context (request or {@code EjecutorEnHogar}).
 */
public interface DatosDeMovimiento {

    /** US-35: the older adult accepted that their movements are stored, and it was not revoked. */
    boolean consentimientoVigente();

    /** US-37: movements may be uploaded to the cloud: consent in force and a retention chosen. */
    boolean guardadoEnLaNube();

    /** Movement files stored in the cloud. */
    long archivosGuardados();

    /**
     * Deletes every movement file of the household from storage and from the index, e.g. when the
     * monitoring consent is revoked (CA-09.1). Call inside a transaction; returns how many.
     */
    long eliminarTodos();
}
