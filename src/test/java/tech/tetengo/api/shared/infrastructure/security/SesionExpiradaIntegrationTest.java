package tech.tetengo.api.shared.infrastructure.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** The app refreshes its tokens only on {@code 401 SESION_EXPIRADA} (API contract, conventions). */
class SesionExpiradaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private static void esSesionExpirada(ResultActions resultado, String instancia) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"))
                .andExpect(jsonPath("$.instance").value(instancia))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void unTokenVencidoRespondeSesionExpiradaEnCualquierEndpointProtegido() throws Exception {
        UUID usuario = UUID.randomUUID();
        UUID hogar = DatosDePrueba.hogar(jdbc, usuario, "Rosa");
        String vencido = JwtDePrueba.tokenVencido(usuario, hogar, Rol.TITULAR);

        for (String ruta : new String[] {"/api/hogar", "/api/camaras", "/api/alertas"}) {
            ResultActions resultado = mvc.perform(get(ruta).header("Authorization", bearer(vencido)));
            esSesionExpirada(resultado, ruta);
            resultado.andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
        }
        // With a valid token the same request succeeds.
        mvc.perform(get("/api/camaras").header("Authorization", bearer(JwtDePrueba.token(usuario, hogar, Rol.TITULAR))))
                .andExpect(status().isOk());
    }

    @Test
    void unTokenInvalidoTambienRespondeSesionExpirada() throws Exception {
        esSesionExpirada(
                mvc.perform(get("/api/agente/estado-captura").header("Authorization", bearer("no-es-un-jwt"))),
                "/api/agente/estado-captura");
    }

    @Test
    void sinTokenSeRespondeSesionExpiradaConElDesafioBearer() throws Exception {
        esSesionExpirada(mvc.perform(get("/api/alertas")), "/api/alertas");
    }
}
