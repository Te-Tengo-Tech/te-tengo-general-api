package tech.tetengo.api.camaras.interfaces.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** The key multi-tenancy test: a household never sees or changes another household's data. */
class CamarasMultitenancyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID hogarA;
    UUID hogarB;
    UUID camaraDeB = UUID.randomUUID();

    @BeforeEach
    void datos() {
        var ahora = java.sql.Timestamp.from(Instant.now());
        hogarA = DatosDePrueba.hogar(jdbc);
        hogarB = DatosDePrueba.hogar(jdbc);
        insertarCamara(UUID.randomUUID(), hogarA, "Sala", ahora);
        insertarCamara(UUID.randomUUID(), hogarA, "Dormitorio", ahora);
        insertarCamara(camaraDeB, hogarB, "Cocina", ahora);
    }

    private void insertarCamara(UUID id, UUID hogar, String nombre, java.sql.Timestamp ahora) {
        jdbc.update(
                "insert into camaras (id, hogar_id, nombre_habitacion, estado_conexion, creado_en, actualizado_en)"
                        + " values (?, ?, ?, 'DESCONECTADA', ?, ?)",
                id,
                hogar,
                nombre,
                ahora,
                ahora);
    }

    @Test
    void cadaHogarVeSoloSusCamaras() throws Exception {
        mvc.perform(get("/api/camaras").header("Authorization", "Bearer " + JwtDePrueba.tokenDeFamiliar(hogarA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombreHabitacion").value("Dormitorio"));
        mvc.perform(get("/api/camaras").header("Authorization", "Bearer " + JwtDePrueba.tokenDeFamiliar(hogarB)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void unHogarNoPuedeRenombrarLaCamaraDeOtro() throws Exception {
        mvc.perform(patch("/api/camaras/" + camaraDeB)
                        .header("Authorization", "Bearer " + JwtDePrueba.tokenDeFamiliar(hogarA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"Baño\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));
    }

    @Test
    void nombreVacioDevuelveProblemDetail() throws Exception {
        mvc.perform(patch("/api/camaras/" + camaraDeB)
                        .header("Authorization", "Bearer " + JwtDePrueba.tokenDeFamiliar(hogarB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"  \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NOMBRE_VACIO"));
    }

    @Test
    void sinTokenNoHayAcceso() throws Exception {
        mvc.perform(get("/api/camaras")).andExpect(status().isUnauthorized());
    }
}
