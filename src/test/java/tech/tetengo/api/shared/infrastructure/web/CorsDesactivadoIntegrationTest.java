package tech.tetengo.api.shared.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.support.AbstractIntegrationTest;

/** Without {@code TT_CORS_ORIGENES} (production's default) no browser on another origin can call the API. */
class CorsDesactivadoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void sinOrigenesConfiguradosNoSeRespondeNingunaCabeceraCors() throws Exception {
        mvc.perform(options("/api/sesiones")
                        .header("Origin", "https://te-tengo-tech.github.io")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/camaras").header("Origin", "https://te-tengo-tech.github.io"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
