package tech.tetengo.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        return solicitar(mvc, nombrePc, null);
    }

    /** The same, from an agent that sends its installation id ({@code id_instalacion}). */
    public static String solicitar(MockMvc mvc, String nombrePc, String idInstalacion) throws Exception {
        var pedido = post("/api/agente/vinculacion")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("client_id", CLIENTE)
                .param("nombre_pc", nombrePc)
                .param("sistema_operativo", "Windows 11 Home")
                .param("version_agente", "0.5.0");
        if (idInstalacion != null) {
            pedido.param("id_instalacion", idInstalacion);
        }
        return mvc.perform(pedido)
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

    /** The linked PC uses a webcam in a room (US-29); returns the {@code CamaraDeLaPc} JSON with its token. */
    public static ResultActions usarCamara(
            MockMvc mvc, String tokenPc, String dispositivoId, String nombreDispositivo, String ambiente)
            throws Exception {
        return mvc.perform(put("/api/agente/pc/camaras")
                .header("Authorization", bearer(tokenPc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dispositivoId\":\"%s\",\"nombreDispositivo\":\"%s\",\"nombreAmbiente\":\"%s\"}"
                        .formatted(dispositivoId.replace("\\", "\\\\"), nombreDispositivo, ambiente)));
    }

    /** The PC reports the webcams it detects (US-29): {@code dispositivoId:nombre} pairs, all plugged in. */
    public static ResultActions reportarWebcams(MockMvc mvc, String tokenPc, String... webcams) throws Exception {
        StringBuilder lista = new StringBuilder();
        for (String webcam : webcams) {
            String[] partes = webcam.split(":", 2);
            if (!lista.isEmpty()) {
                lista.append(',');
            }
            lista.append("{\"dispositivoId\":\"%s\",\"nombreDispositivo\":\"%s\",\"conectada\":true}"
                    .formatted(partes[0], partes[1]));
        }
        return mvc.perform(put("/api/agente/pc/camaras/inventario")
                .header("Authorization", bearer(tokenPc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"webcams\":[" + lista + "]}"));
    }

    /** The owner assigns a webcam to a room from the app (US-29). */
    public static ResultActions asignarDesdeLaApp(MockMvc mvc, String tokenTitular, String camaraId, String ambiente)
            throws Exception {
        return mvc.perform(put("/api/camaras/" + camaraId + "/ambiente")
                .header("Authorization", bearer(tokenTitular))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreAmbiente\":\"%s\"}".formatted(ambiente)));
    }

    /**
     * The whole link: the PC asks, the owner approves, the PC polls once. Returns the token response
     * JSON ({@code access_token}, {@code refresh_token}, {@code pc_id}, {@code hogar_id}).
     */
    public static String vincular(MockMvc mvc, String tokenTitular, String nombrePc) throws Exception {
        return vincular(mvc, tokenTitular, nombrePc, null);
    }

    /** The same, from an agent that sends its installation id. */
    public static String vincular(MockMvc mvc, String tokenTitular, String nombrePc, String idInstalacion)
            throws Exception {
        String solicitud = solicitar(mvc, nombrePc, idInstalacion);
        resolver(mvc, tokenTitular, "aprobacion", campo(solicitud, "$.user_code"))
                .andExpect(status().isCreated());
        return consultar(mvc, campo(solicitud, "$.device_code"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
