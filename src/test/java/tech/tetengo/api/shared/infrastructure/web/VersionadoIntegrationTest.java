package tech.tetengo.api.shared.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.support.AbstractIntegrationTest;

/** Header versioning: {@code Api-Version: 1} is optional and the only supported version. */
class VersionadoIntegrationTest extends AbstractIntegrationTest {

    private static final String CUENTA =
            "{\"correo\":\"ana@correo.pe\",\"contrasena\":\"secreta123\",\"nombre\":\"Ana\"}";

    @Autowired
    MockMvc mvc;

    @Test
    void laVersionUnoEsLaPredeterminada() throws Exception {
        mvc.perform(post("/api/cuentas").contentType(MediaType.APPLICATION_JSON).content(CUENTA))
                .andExpect(status().isCreated());
    }

    @Test
    void laCabeceraDeVersionUnoSeAcepta() throws Exception {
        mvc.perform(post("/api/cuentas")
                        .header("Api-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUENTA))
                .andExpect(status().isCreated());
    }

    @Test
    void unaVersionNoSoportadaEsUnProblemDetailConCodigo() throws Exception {
        mvc.perform(post("/api/cuentas")
                        .header("Api-Version", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUENTA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
}
