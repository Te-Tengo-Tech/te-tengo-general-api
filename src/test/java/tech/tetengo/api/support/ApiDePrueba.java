package tech.tetengo.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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

    /**
     * Registers an account, signs in and creates its household (US-01, US-02, US-04). Returns the
     * owner's {@code Sesion} JSON, whose token carries the household.
     */
    public static String titularConHogar(MockMvc mvc, String correo, String nombre) throws Exception {
        registrarCuenta(mvc, correo, "secreta123", nombre);
        String sesion = iniciarSesion(mvc, correo, "secreta123");
        return mvc.perform(post("/api/hogar")
                        .header("Authorization", bearer(campo(sesion, "$.tokenAcceso")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"adultoMayor\":{\"nombre\":\"Adulto de %s\",\"direccion\":\"Lima\",\"convivencia\":\"SOLO\"}}"
                                        .formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** The owner grants the older adult's consent (US-05). */
    public static void otorgarConsentimiento(MockMvc mvc, String tokenTitular) throws Exception {
        mvc.perform(
                        post("/api/hogar/consentimiento")
                                .header("Authorization", bearer(tokenTitular))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"otorgadoPor\":\"Rosa\",\"aceptadoPorAdultoMayor\":true,\"vistaEnVivoAceptada\":true}"))
                .andExpect(status().isCreated());
    }

    /** The household agent registers its camera (AGENT_CONTRACT.md); returns the response JSON. */
    public static String registrarAgente(MockMvc mvc, String credencial, String habitacion) throws Exception {
        return mvc.perform(post("/api/agente/camaras/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"credencialInstalacion\":\"%s\",\"nombreHabitacion\":\"%s\",\"versionAgente\":\"1.0.0\"}"
                                        .formatted(credencial, habitacion)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * Grants consent in the owner's household, registers its agent and waits until capture is
     * allowed (the capture state is updated asynchronously). Returns the registration JSON
     * ({@code camaraId}, {@code token}).
     */
    public static String agenteConConsentimiento(
            MockMvc mvc, JdbcTemplate jdbc, String sesionTitular, String habitacion) throws Exception {
        otorgarConsentimiento(mvc, campo(sesionTitular, "$.tokenAcceso"));
        String registro = registrarAgente(
                mvc, DatosDePrueba.instalacion(jdbc, UUID.fromString(campo(sesionTitular, "$.hogarId"))), habitacion);
        String token = campo(registro, "$.token");
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> campo(
                                mvc.perform(get("/api/agente/estado-captura").header("Authorization", bearer(token)))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString(),
                                "$.capturaPermitida")
                        .equals("true"));
        return registro;
    }

    /** The agent sends a detected event (AGENT_CONTRACT.md). */
    public static ResultActions enviarEvento(
            MockMvc mvc, String tokenAgente, UUID eventoId, String tipo, Instant ocurridoEn) throws Exception {
        return mvc.perform(post("/api/agente/eventos")
                .header("Authorization", bearer(tokenAgente))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"eventoId\":\"%s\",\"tipo\":\"%s\",\"ocurridoEn\":\"%s\",\"parametros\":{\"angulo_grados\":22.1}}"
                                .formatted(eventoId, tipo, ocurridoEn)));
    }

    public static String campo(String json, String ruta) {
        Object valor = JsonPath.read(json, ruta);
        return valor == null ? null : valor.toString();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
