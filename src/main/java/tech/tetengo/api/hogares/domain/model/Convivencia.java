package tech.tetengo.api.hogares.domain.model;

/** Who the older adult lives with, from the prototype's profile screen (API contract §2). */
public enum Convivencia {
    /** «Vive solo(a)». */
    SOLO,
    /** «Vive conmigo»: lives with the account owner. */
    CON_FAMILIAR,
    /** «Vive con otro cuidador». */
    CON_CUIDADOR
}
