package tech.tetengo.api.monitoreo.domain.model;

/**
 * Why an attempt to reach a camera's live view was refused (ADR 0015). Only security denials: a paused or
 * disconnected camera refuses the live view too, but nobody tried to get around a rule.
 */
public enum MotivoDeDenegacion {

    // API: the app's requests.

    /** The camera is not in the requester's household: another household's camera, or no camera at all. */
    CAMARA_NO_ENCONTRADA,
    /** The older adult's consent (with its live view clause) is not in force (CA-05.2). */
    SIN_CONSENTIMIENTO,
    /** Changing or closing a live view session of another member or of another household. */
    SESION_AJENA,

    // MEDIAMTX: the authorization hook.

    /** The hook was called from outside the private networks. */
    ORIGEN_EXTERNO,
    /** Without the hook's shared secret, or with another one. */
    SECRETO_INVALIDO,
    /** {@code api}, {@code metrics}, {@code pprof} or any other action that is never allowed. */
    ACCION_NO_PERMITIDA,
    /** A read over a protocol other than HLS or WebRTC (WHEP). */
    PROTOCOLO_NO_PERMITIDO,
    /** A path that is not {@code camaras/<camaraId>}. */
    RUTA_INVALIDA,
    /** A publish without user {@code agente} or without the publish token of the camera's transmission. */
    CLAVE_DE_PUBLICACION_INVALIDA,
    /** A webcam «sin usar» publishing while no preview of it is open (CA-29.7). */
    PUBLICACION_SIN_VISTA_PREVIA,
    /** A read without a viewer token. */
    SIN_TOKEN,
    /** A read with a viewer token of no session. */
    TOKEN_INVALIDO,
    /** A read with the viewer token of another camera's session. */
    TOKEN_DE_OTRA_CAMARA,
    /** A read with the token of a session that ended or reached its maximum length. */
    SESION_TERMINADA,
    /** A read of a webcam «sin usar» with a token that is not of its preview (CA-29.7). */
    LECTURA_SIN_VISTA_PREVIA,
    /** The camera may not capture now (paused, no consent), so it neither publishes nor is read. */
    CAPTURA_NO_PERMITIDA
}
