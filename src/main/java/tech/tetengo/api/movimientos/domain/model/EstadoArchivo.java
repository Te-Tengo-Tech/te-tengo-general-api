package tech.tetengo.api.movimientos.domain.model;

public enum EstadoArchivo {
    /** The upload URL was issued; the object may not exist yet. */
    PENDIENTE,
    /** The PC confirmed it and the storage has it: part of the index. */
    DISPONIBLE
}
