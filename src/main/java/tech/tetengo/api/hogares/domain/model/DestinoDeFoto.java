package tech.tetengo.api.hogares.domain.model;

/** Whose photo an upload replaces (API contract §13, {@code de}). */
public enum DestinoDeFoto {
    /** The caller's own photo. */
    YO,
    /** The older adult's photo; only the owner changes it. */
    ADULTO_MAYOR
}
