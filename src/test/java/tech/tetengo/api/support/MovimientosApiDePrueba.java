package tech.tetengo.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;

import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Movement data through the real API (US-35, US-37). */
public final class MovimientosApiDePrueba {

    public static final String SHA = "a".repeat(64);

    private MovimientosApiDePrueba() {}

    public static ResultActions otorgar(MockMvc mvc, String tokenTitular) throws Exception {
        return mvc.perform(
                post("/api/hogar/consentimiento-movimientos")
                        .header("Authorization", bearer(tokenTitular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"otorgadoPor\":\"Rosa Huamán\",\"representante\":false,\"aceptado\":true,\"versionTexto\":\"1\"}"));
    }

    public static ResultActions retencion(MockMvc mvc, String tokenTitular, Integer dias) throws Exception {
        return mvc.perform(put("/api/hogar/movimientos-en-la-nube")
                .header("Authorization", bearer(tokenTitular))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retencionDias\":%s}".formatted(dias)));
    }

    /** The consent and a cloud retention of {@code dias}. */
    public static void guardarEnLaNube(MockMvc mvc, String tokenTitular, int dias) throws Exception {
        otorgar(mvc, tokenTitular).andExpect(status().isCreated());
        retencion(mvc, tokenTitular, dias).andExpect(status().isOk());
    }

    public static ResultActions pedirSubida(MockMvc mvc, String tokenCamara, Instant desde, Instant hasta)
            throws Exception {
        return mvc.perform(post("/api/agente/movimientos")
                .header("Authorization", bearer(tokenCamara))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"desde\":\"%s\",\"hasta\":\"%s\",\"tamanoBytes\":4096,\"sha256\":\"%s\",\"fotogramas\":2400}"
                                .formatted(desde, hasta, SHA)));
    }

    public static ResultActions confirmar(MockMvc mvc, String tokenCamara, String archivoId) throws Exception {
        return mvc.perform(post("/api/agente/movimientos/" + archivoId + "/confirmacion")
                .header("Authorization", bearer(tokenCamara)));
    }
}
