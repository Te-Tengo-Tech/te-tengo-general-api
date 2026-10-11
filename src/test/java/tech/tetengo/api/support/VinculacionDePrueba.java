package tech.tetengo.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The PC link through the real API (RFC 8628 device grant, AGENT_CONTRACT.md "PC link"). */
public final class VinculacionDePrueba {

    public static final String CLIENTE = "te-tengo-captura";
    public static final String CONCESION_DISPOSITIVO = "urn:ietf:params:oauth:grant-type:device_code";

    private VinculacionDePrueba() {}

    /** The PC asks to be linked; returns the device authorization response JSON. */
    public static String solicitar(MockMvc mvc, String nombrePc) throws Exception {
        return mvc.perform(post("/api/agente/vinculacion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", CLIENTE)
                        .param("nombre_pc", nombrePc)
                        .param("sistema_operativo", "Windows 11 Home")
                        .param("version_agente", "0.5.0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** The PC polls the token endpoint with its device code. */
    public static ResultActions consultar(MockMvc mvc, String codigoDispositivo) throws Exception {
        return mvc.perform(post("/api/agente/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", CONCESION_DISPOSITIVO)
                .param("device_code", codigoDispositivo)
                .param("client_id", CLIENTE));
    }

    /** The PC renews its tokens. */
    public static ResultActions renovar(MockMvc mvc, String tokenRenovacion) throws Exception {
        return mvc.perform(post("/api/agente/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", tokenRenovacion)
                .param("client_id", CLIENTE));
    }

    /** The owner acts on a code: {@code consulta}, {@code aprobacion} or {@code rechazo}. */
    public static ResultActions resolver(MockMvc mvc, String tokenMiembro, String accion, String codigo)
            throws Exception {
        return mvc.perform(post("/api/vinculaciones/" + accion)
                .header("Authorization", bearer(tokenMiembro))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"%s\"}".formatted(codigo)));
    }

    /**
     * The whole link: the PC asks, the owner approves, the PC polls once. Returns the token response
     * JSON ({@code access_token}, {@code refresh_token}, {@code pc_id}, {@code hogar_id}).
     */
    public static String vincular(MockMvc mvc, String tokenTitular, String nombrePc) throws Exception {
        String solicitud = solicitar(mvc, nombrePc);
        resolver(mvc, tokenTitular, "aprobacion", campo(solicitud, "$.user_code"))
                .andExpect(status().isCreated());
        return consultar(mvc, campo(solicitud, "$.device_code"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
