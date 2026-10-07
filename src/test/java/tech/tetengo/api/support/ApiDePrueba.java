package tech.tetengo.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Common flows through the real API, so tests read like the story they cover. */
public final class ApiDePrueba {

    private ApiDePrueba() {}

    /** Registers an account (US-01) and returns its id. */
    public static UUID registrarCuenta(MockMvc mvc, String correo, String contrasena, String nombre) throws Exception {
        String cuerpo = mvc.perform(post("/api/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\",\"nombre\":\"%s\"}"
                                .formatted(correo, contrasena, nombre)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    }

    /** Signs in (US-02) and returns the {@code Sesion} JSON. */
    public static String iniciarSesion(MockMvc mvc, String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/sesiones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    public static String campo(String json, String ruta) {
        Object valor = JsonPath.read(json, ruta);
        return valor == null ? null : valor.toString();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
