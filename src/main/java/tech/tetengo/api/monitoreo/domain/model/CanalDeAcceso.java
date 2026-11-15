package tech.tetengo.api.monitoreo.domain.model;

/** Where a denied live view attempt came in (ADR 0015). */
public enum CanalDeAcceso {
    /** A request of the app to the API. */
    API,
    /** MediaMTX's authorization hook: a publish or a read of the stream itself. */
    MEDIAMTX
}
