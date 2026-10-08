package tech.tetengo.api.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/**
 * The PWA build of the app, served from GitHub Pages: the browser calls the API from another origin
 * (CORS, preflight included, through Spring Security) and e-mailed links open the PWA's hash routes.
 */
@TestPropertySource(
        properties = {
            "tetengo.cors.origenes=" + ClienteWebIntegrationTest.PAGINAS + ", http://localhost:*",
            "tetengo.enlaces.base=" + ClienteWebIntegrationTest.PWA + "#"
        })
class ClienteWebIntegrationTest extends AbstractIntegrationTest {

    static final String PAGINAS = "https://te-tengo-tech.github.io";
    static final String PWA = PAGINAS + "/te-tengo-descargas/app/";
    static final String OTRO = "https://otro-sitio.example";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions preflight(String ruta, String origen, String metodo, String cabeceras) throws Exception {
        return mvc.perform(options(ruta)
                .header("Origin", origen)
                .header("Access-Control-Request-Method", metodo)
                .header("Access-Control-Request-Headers", cabeceras));
    }

    private String tokenDeTitular() {
        UUID usuario = UUID.randomUUID();
        return JwtDePrueba.token(usuario, DatosDePrueba.hogar(jdbc, usuario, "Rosa"), Rol.TITULAR);
    }

    @Test
    void elPreflightDeUnOrigenPermitidoSeRespondeSinTokenConLasCabecerasDeLaApp() throws Exception {
        preflight("/api/camaras", PAGINAS, "GET", "authorization, api-version, content-type")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PAGINAS))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("api-version")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("content-type")))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        preflight("/api/camaras/" + UUID.randomUUID(), PAGINAS, "PATCH", "authorization, content-type")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    void unPatronDeOrigenPermiteLaPwaServidaEnLocal() throws Exception {
        preflight("/api/sesiones", "http://localhost:5173", "POST", "content-type")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void unaPeticionDeUnOrigenPermitidoPuedeLeerLaRespuestaYElCodigoDeSesionExpirada() throws Exception {
        mvc.perform(get("/api/camaras")
                        .header("Origin", PAGINAS)
                        .header("Authorization", bearer(tokenDeTitular()))
                        .header("Api-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PAGINAS))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("WWW-Authenticate")));
        // Without a token the app must still read 401 SESION_EXPIRADA to refresh its session.
        mvc.perform(get("/api/camaras").header("Origin", PAGINAS))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", PAGINAS))
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
    }

    @Test
    void unOrigenNoPermitidoSeRechaza() throws Exception {
        preflight("/api/camaras", OTRO, "GET", "authorization")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/camaras").header("Origin", OTRO).header("Authorization", bearer(tokenDeTitular())))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        // An origin is scheme, host and port: the same host over plain HTTP is another origin.
        preflight("/api/camaras", "http://te-tengo-tech.github.io", "GET", "authorization")
                .andExpect(status().isForbidden());
    }

    @Test
    void unaCabeceraQueLaAppNoEnviaNoSePermite() throws Exception {
        preflight("/api/camaras", PAGINAS, "GET", "x-otra")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        // Mixed with allowed ones, only those are listed, so the browser blocks the request.
        preflight("/api/camaras", PAGINAS, "GET", "authorization, x-otra")
                .andExpect(header().string("Access-Control-Allow-Headers", not(containsStringIgnoringCase("x-otra"))));
    }

    @Test
    void ca03_1_elEnlaceDeRecuperacionAbreLaRutaDeLaPwa() throws Exception {
        ApiDePrueba.registrarCuenta(mvc, "ana@correo.pe", "olvidada1", "Ana");

        mvc.perform(post("/api/recuperaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@correo.pe\"}"))
                .andExpect(status().isAccepted());

        String token = correos.ultimoToken("ana@correo.pe");
        assertThat(correos.enviadosA("ana@correo.pe").getFirst().cuerpo())
                .contains(PWA + "#/nueva-contrasena?token=" + token)
                .doesNotContain("tetengo://");
    }

    @Test
    void ca08_1_elEnlaceDeInvitacionAbreLaRutaDeLaPwa() throws Exception {
        String titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");

        mvc.perform(post("/api/invitaciones")
                        .header("Authorization", bearer(campo(titular, "$.tokenAcceso")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"beto@correo.pe\"}"))
                .andExpect(status().isCreated());

        String token = correos.ultimoToken("beto@correo.pe");
        assertThat(correos.enviadosA("beto@correo.pe").getFirst().cuerpo())
                .contains(PWA + "#/invitacion/" + token)
                .doesNotContain("tetengo://");
    }
}
