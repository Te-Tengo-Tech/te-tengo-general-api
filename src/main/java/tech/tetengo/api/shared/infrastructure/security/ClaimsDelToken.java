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

    private ClaimsDelToken() {}
}
