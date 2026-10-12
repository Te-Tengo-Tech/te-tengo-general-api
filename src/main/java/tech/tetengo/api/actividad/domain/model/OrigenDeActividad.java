package tech.tetengo.api.actividad.domain.model;

/** Who made the change (CA-33.1: «el familiar o la PC desde la que se hizo»). */
public enum OrigenDeActividad {
    /** A member, from the app. */
    FAMILIAR,
    /** A PC, from Te Tengo Captura. */
    PC,
    /** The system itself: an automatic resume, an expired link, a consequence of another change. */
    SISTEMA
}
