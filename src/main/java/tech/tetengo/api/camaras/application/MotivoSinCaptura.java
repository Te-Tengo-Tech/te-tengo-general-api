package tech.tetengo.api.camaras.application;

/** Why the agent may not capture (AGENT_CONTRACT.md, "Capture state"). */
public enum MotivoSinCaptura {
    /** No current consent of the older adult (CA-05.2). It wins over a pause. */
    SIN_CONSENTIMIENTO,
    /** The camera is paused (CA-22.1). */
    EN_PAUSA
}
