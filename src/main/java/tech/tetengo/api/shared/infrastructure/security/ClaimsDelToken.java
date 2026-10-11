package tech.tetengo.api.shared.infrastructure.security;

/** Claims of the JWTs issued by this API (see {@code docs/API_CONTRACT.md} and {@code AGENT_CONTRACT.md}). */
public final class ClaimsDelToken {

    /** Active household, the tenant. */
    public static final String HOGAR = "hogar_id";

    /** {@code TITULAR}, {@code INVITADO} or {@code AGENTE}. */
    public static final String ROL = "rol";

    /** Session id of a user token, used to close the session (CA-02.4). */
    public static final String SESION = "sid";

    /** Camera of a household agent token. */
    public static final String CAMARA = "camara_id";

    /**
     * PC of a household agent token issued through the PC link (RFC 8628 device grant): the PC's own
     * access token, and the per-camera tokens of its cameras.
     */
    public static final String PC = "pc_id";

    private ClaimsDelToken() {}
}
